package de.lembergmax.gitmax.ui.viewer;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.text.method.KeyListener;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.databinding.DialogNewFolderBinding;
import de.lembergmax.gitmax.databinding.FragmentFileViewerBinding;
import de.lembergmax.gitmax.domain.GitLfs;
import de.lembergmax.gitmax.domain.syntax.SyntaxLanguage;
import de.lembergmax.gitmax.storage.SharedPreferencesKeyValueStore;
import de.lembergmax.gitmax.storage.TextFileIo;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.ExternalFiles;
import de.lembergmax.gitmax.ui.common.InsetsPadding;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;
import de.lembergmax.gitmax.ui.repo.RepoDetailFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zeigt eine Datei und bearbeitet sie: Text mit Zeilennummern, Suchen und Ersetzen, Zeilensprung,
 * Zeilenumbruch, Bild-Vorschau und Hinweise für Binär- und zu große Dateien.
 */
public final class FileViewerFragment extends Fragment {

    /** Pfad des Repos. */
    public static final String ARG_DIRECTORY = "directory";

    /** Pfad der Datei im Repo, relativ und mit {@code /}. */
    public static final String ARG_PATH = "path";

    /** {@code true}: die Datei sofort zum Bearbeiten öffnen (sofern sie sich bearbeiten lässt). */
    public static final String ARG_EDIT = "edit";

    private static final String STATE_FILE = "gitmax_state";
    private static final String KEY_WRAP = "viewer.wrap";
    private static final String STATE_EDITING = "editing";
    private static final String STATE_DIRTY = "dirty";
    private static final int MAX_MATCHES = 5000;
    private static final String[] KEYS = {"⇥", "(", ")", "[", "]", "{", "}", "<", ">", "\"", "'", ";", ":", "=", "/", "\\", "-", "_", "#"};

    private FragmentFileViewerBinding binding;
    private FileViewerViewModel viewModel;
    private OnBackPressedCallback leaveGuard;
    private KeyListener editKeyListener;
    private HorizontalScrollView horizontalHolder;
    private SharedPreferencesKeyValueStore preferences;

    private boolean editing;
    private boolean dirty;
    private boolean wrap = true;
    private boolean textShown;
    private boolean readOnlyHintVisible;
    private boolean suppressDirty;
    private boolean openInEditMode;
    private SyntaxController syntax;

    private final List<Integer> matches = new ArrayList<>();
    private int currentMatch = -1;

    public FileViewerFragment() {
        super(R.layout.fragment_file_viewer);
    }

    @Override
    public void onCreate(
            @Nullable final Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        setEnterTransition(Motion.sharedAxis(requireContext(), MaterialSharedAxis.X, true));
        setReturnTransition(Motion.sharedAxis(requireContext(), MaterialSharedAxis.X, false));
        setExitTransition(Motion.sharedAxis(requireContext(), MaterialSharedAxis.X, true));
        setReenterTransition(Motion.sharedAxis(requireContext(), MaterialSharedAxis.X, false));
        if (savedInstanceState != null) {
            editing = savedInstanceState.getBoolean(STATE_EDITING);
            dirty = savedInstanceState.getBoolean(STATE_DIRTY);
        } else {
            openInEditMode = requireArguments().getBoolean(ARG_EDIT);
        }
    }

    @Override
    public void onSaveInstanceState(
            @NonNull final Bundle outState
    ) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_EDITING, editing);
        outState.putBoolean(STATE_DIRTY, dirty);
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        // Die Fragment-Instanz überlebt ihre View (Zurück aus Blame): Text und Suche gehören zur neuen View.
        textShown = false;
        matches.clear();
        currentMatch = -1;
        binding = FragmentFileViewerBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(FileViewerViewModel.class);
        preferences = new SharedPreferencesKeyValueStore(requireContext(), STATE_FILE);
        wrap = !"0".equals(preferences.get(KEY_WRAP).orElse("1"));
        final String directory = requireArguments().getString(ARG_DIRECTORY);
        final String path = requireArguments().getString(ARG_PATH);
        if (directory == null || path == null) {
            throw new IllegalStateException("No file passed");
        }
        viewModel.init(directory, path);
        syntax = new SyntaxController(binding.code, binding.textScroll, ServiceLocator.from(requireContext()).io(),
                SyntaxColors.load(requireContext()));
        syntax.setLanguage(SyntaxLanguage.forFileName(viewModel.file().getName()));
        binding.textScroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (scrolled, x, y, oldX, oldY) ->
                syntax.viewChanged());

        Toolbars.setupBack(this, binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(button -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.toolbar.setTitle(viewModel.file().getName());
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItem);

        editKeyListener = binding.code.getKeyListener();
        horizontalHolder = new HorizontalScrollView(requireContext());
        horizontalHolder.setFillViewport(true);
        horizontalHolder.setHorizontalScrollBarEnabled(false);
        applyWrap();
        setupAutoIndent();
        setupFind();
        buildKeyBar();
        InsetsPadding.apply(binding.keyBar, false, true, false);

        leaveGuard = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                confirmLeave();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), leaveGuard);

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.saveOutcomes().observe(getViewLifecycleOwner(), this::onSaveOutcome);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (root, insets) -> {
            final boolean keyboard = insets.isVisible(WindowInsetsCompat.Type.ime());
            binding.keyBar.setVisibility(editing && keyboard ? View.VISIBLE : View.GONE);
            return insets;
        });
    }

    @Override
    public void onDestroyView() {
        syntax.stop();
        // Ungespeicherte Eingaben überstehen die Drehung des Bildschirms im ViewModel.
        viewModel.keepDraft(editing && dirty ? String.valueOf(binding.code.getText()) : null);
        binding = null;
        super.onDestroyView();
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Zeichnen                                                                                   */
    /* ------------------------------------------------------------------------------------------ */

    private void render(
            @NonNull final FileViewerViewModel.UiState state
    ) {
        binding.progress.setVisibility(state.content() == FileViewerViewModel.Content.LOADING ? View.VISIBLE : View.INVISIBLE);
        binding.textScroll.setVisibility(state.content() == FileViewerViewModel.Content.TEXT ? View.VISIBLE : View.GONE);
        binding.image.setVisibility(state.content() == FileViewerViewModel.Content.IMAGE ? View.VISIBLE : View.GONE);
        binding.info.setVisibility(isInfo(state.content()) ? View.VISIBLE : View.GONE);

        switch (state.content()) {
            case TEXT:
                showText(state);
                break;
            case IMAGE:
                binding.image.setImageBitmap(state.image());
                binding.toolbar.setSubtitle(Formatter.formatShortFileSize(requireContext(), state.size()));
                break;
            case BINARY:
                showInfo(R.drawable.ic_description, R.string.viewer_binary_title,
                        getString(R.string.viewer_binary_body, Formatter.formatShortFileSize(requireContext(), state.size())), 0, null);
                break;
            case NOT_UTF8:
                showInfo(R.drawable.ic_description, R.string.viewer_not_utf8_title, getString(R.string.viewer_not_utf8_body), 0, null);
                break;
            case TOO_LARGE:
                showInfo(R.drawable.ic_description, R.string.viewer_too_large_title,
                        getString(R.string.viewer_too_large_body, Formatter.formatShortFileSize(requireContext(), state.size())),
                        R.string.viewer_open_anyway, () -> viewModel.openAnyway());
                break;
            case ERROR:
                showInfo(R.drawable.ic_error, R.string.files_error_not_found, "", 0, null);
                break;
            case LOADING:
            default:
                break;
        }
        updateMenu(state);
    }

    private static boolean isInfo(
            final FileViewerViewModel.Content content
    ) {
        return content == FileViewerViewModel.Content.BINARY || content == FileViewerViewModel.Content.NOT_UTF8
                || content == FileViewerViewModel.Content.TOO_LARGE || content == FileViewerViewModel.Content.ERROR;
    }

    private void showInfo(
            final int icon,
            final int title,
            @NonNull final String body,
            final int actionText,
            @Nullable final Runnable action
    ) {
        binding.infoIcon.setImageResource(icon);
        binding.infoTitle.setText(title);
        binding.infoBody.setText(body);
        binding.infoBody.setVisibility(body.isEmpty() ? View.GONE : View.VISIBLE);
        if (action == null) {
            binding.infoAction.setVisibility(View.GONE);
        } else {
            binding.infoAction.setVisibility(View.VISIBLE);
            binding.infoAction.setText(actionText);
            binding.infoAction.setOnClickListener(button -> action.run());
        }
    }

    private void showText(
            @NonNull final FileViewerViewModel.UiState state
    ) {
        final TextFileIo.Result text = state.text();
        if (text == null) {
            return;
        }
        if (!textShown) {
            textShown = true;
            final Optional<String> draft = viewModel.draft();
            if (dirty && draft.isEmpty()) {
                // Nach dem Ende des Prozesses ist der Entwurf weg; die Datei selbst ist unverändert.
                dirty = false;
            }
            suppressDirty = true;
            binding.code.setText(dirty && editing ? draft.orElse(text.text()) : text.text(), TextView.BufferType.EDITABLE);
            suppressDirty = false;
            syntax.textLoaded();
            applyMode();
            if (openInEditMode && text.isEditable() && !editing) {
                openInEditMode = false;
                enterEditMode();
            }
        }
        final int lines = text.text().isEmpty() ? 0 : countLines(text.text());
        binding.toolbar.setSubtitle(getResources().getQuantityString(R.plurals.viewer_line_count, lines, lines)
                + " · " + (text.lineEnding() == TextFileIo.LineEnding.CRLF ? "CRLF" : text.lineEnding() == TextFileIo.LineEnding.MIXED ? "LF/CRLF" : "LF"));
        final Optional<GitLfs.Pointer> lfsPointer = GitLfs.parsePointer(text.text());
        final int hint = !text.isEditable()
                ? (text.lineEnding() == TextFileIo.LineEnding.MIXED ? R.string.viewer_read_only_mixed : R.string.viewer_read_only_large)
                : 0;
        readOnlyHintVisible = hint != 0 || lfsPointer.isPresent();
        binding.hint.setVisibility(readOnlyHintVisible ? View.VISIBLE : View.GONE);
        if (lfsPointer.isPresent()) {
            binding.hint.setText(getString(R.string.viewer_lfs_pointer,
                    Formatter.formatShortFileSize(requireContext(), lfsPointer.get().size())));
        } else if (hint != 0) {
            binding.hint.setText(hint);
        }
    }

    private static int countLines(
            final String text
    ) {
        int lines = 1;
        for (int index = text.indexOf('\n'); index >= 0 && index < text.length() - 1; index = text.indexOf('\n', index + 1)) {
            lines += 1;
        }
        return lines;
    }

    private void updateMenu(
            @NonNull final FileViewerViewModel.UiState state
    ) {
        final Menu menu = binding.toolbar.getMenu();
        final boolean isText = state.content() == FileViewerViewModel.Content.TEXT;
        final boolean editable = isText && state.text() != null && state.text().isEditable();
        menu.findItem(R.id.action_edit).setVisible(isText && editable && !editing);
        menu.findItem(R.id.action_done).setVisible(editing);
        menu.findItem(R.id.action_save).setVisible(editing);
        menu.findItem(R.id.action_save).setEnabled(dirty);
        menu.findItem(R.id.action_undo).setVisible(editing);
        menu.findItem(R.id.action_redo).setVisible(editing);
        menu.findItem(R.id.action_find).setVisible(isText);
        menu.findItem(R.id.action_wrap).setVisible(isText);
        menu.findItem(R.id.action_wrap).setChecked(wrap);
        menu.findItem(R.id.action_goto_line).setVisible(isText);
        menu.findItem(R.id.action_save_and_commit).setVisible(editing);
        menu.findItem(R.id.action_blame).setVisible(isText && !editing);
        leaveGuard.setEnabled(editing && dirty);
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Bearbeiten                                                                                 */
    /* ------------------------------------------------------------------------------------------ */

    private void applyMode() {
        final CodeEditText code = binding.code;
        // setTextIsSelectable setzt den Text intern neu; das ist keine Eingabe und darf die Datei nicht als geändert markieren.
        final boolean wasSuppressed = suppressDirty;
        suppressDirty = true;
        if (editing) {
            code.setTextIsSelectable(false);
            code.setKeyListener(editKeyListener);
            code.setCursorVisible(true);
            code.setShowSoftInputOnFocus(true);
        } else {
            code.setKeyListener(null);
            code.setTextIsSelectable(true);
            code.setCursorVisible(false);
        }
        suppressDirty = wasSuppressed;
        binding.replaceRow.setVisibility(editing && binding.findBar.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
    }

    private void enterEditMode() {
        editing = true;
        applyMode();
        binding.code.requestFocus();
        final InputMethodManager keyboard = requireContext().getSystemService(InputMethodManager.class);
        keyboard.showSoftInput(binding.code, InputMethodManager.SHOW_IMPLICIT);
        refreshMenu();
    }

    private void openBlame() {
        final Bundle arguments = new Bundle();
        arguments.putString(BlameFragment.ARG_DIRECTORY, requireArguments().getString(ARG_DIRECTORY));
        arguments.putString(BlameFragment.ARG_PATH, viewModel.path());
        NavHostFragment.findNavController(this).navigate(R.id.blameFragment, arguments);
    }

    private void leaveEditMode() {
        editing = false;
        dirty = false;
        applyMode();
        hideKeyboard();
        binding.keyBar.setVisibility(View.GONE);
        refreshMenu();
    }

    private void refreshMenu() {
        final FileViewerViewModel.UiState state = viewModel.state().getValue();
        if (state != null) {
            updateMenu(state);
        }
    }

    private void hideKeyboard() {
        final InputMethodManager keyboard = requireContext().getSystemService(InputMethodManager.class);
        keyboard.hideSoftInputFromWindow(binding.code.getWindowToken(), 0);
    }

    private void setupAutoIndent() {
        binding.code.addTextChangedListener(new TextWatcher() {
            private boolean newlineInserted;
            private int insertedAt;

            @Override
            public void beforeTextChanged(
                    final CharSequence text,
                    final int start,
                    final int count,
                    final int after
            ) {
                // nichts vorzubereiten
            }

            @Override
            public void onTextChanged(
                    final CharSequence text,
                    final int start,
                    final int before,
                    final int count
            ) {
                newlineInserted = editing && !suppressDirty && before == 0 && count == 1 && text.charAt(start) == '\n';
                insertedAt = start;
            }

            @Override
            public void afterTextChanged(
                    final Editable editable
            ) {
                syntax.textChanged();
                if (suppressDirty) {
                    return;
                }
                if (!dirty && editing) {
                    dirty = true;
                    refreshMenu();
                }
                if (newlineInserted) {
                    newlineInserted = false;
                    copyIndent(editable, insertedAt);
                }
            }
        });
    }

    /** Übernimmt die Einrückung der vorigen Zeile in die neue. */
    private void copyIndent(
            final Editable editable,
            final int newlineAt
    ) {
        int lineStart = newlineAt;
        while (lineStart > 0 && editable.charAt(lineStart - 1) != '\n') {
            lineStart -= 1;
        }
        int end = lineStart;
        while (end < newlineAt && (editable.charAt(end) == ' ' || editable.charAt(end) == '\t')) {
            end += 1;
        }
        if (end > lineStart) {
            suppressDirty = true;
            editable.insert(newlineAt + 1, editable.subSequence(lineStart, end));
            suppressDirty = false;
        }
    }

    private void buildKeyBar() {
        final LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (final String key : KEYS) {
            final TextView button = (TextView) inflater.inflate(R.layout.item_editor_key, binding.keys, false);
            button.setText(key);
            button.setContentDescription(key.equals("⇥") ? "Tab" : key);
            button.setOnClickListener(view -> insertKey(key));
            binding.keys.addView(button);
        }
    }

    private void insertKey(
            @NonNull final String key
    ) {
        final Editable editable = binding.code.getText();
        if (editable == null) {
            return;
        }
        final String insert = key.equals("⇥") ? indentUnit(editable) : key;
        final int start = Math.max(0, binding.code.getSelectionStart());
        final int end = Math.max(0, binding.code.getSelectionEnd());
        editable.replace(Math.min(start, end), Math.max(start, end), insert);
    }

    /** Ein Tabulator, wenn die Datei Tabs zum Einrücken nutzt, sonst vier Leerzeichen. */
    private static String indentUnit(
            final CharSequence text
    ) {
        for (int index = 0; index < text.length() - 1; index += 1) {
            if (text.charAt(index) == '\n' && text.charAt(index + 1) == '\t') {
                return "\t";
            }
        }
        return "    ";
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Menü                                                                                       */
    /* ------------------------------------------------------------------------------------------ */

    private boolean onMenuItem(
            @NonNull final MenuItem item
    ) {
        final int id = item.getItemId();
        if (id == R.id.action_edit) {
            enterEditMode();
        } else if (id == R.id.action_done) {
            if (dirty) {
                confirmLeave();
            } else {
                leaveEditMode();
            }
        } else if (id == R.id.action_save) {
            save(false, false);
        } else if (id == R.id.action_undo) {
            binding.code.onTextContextMenuItem(android.R.id.undo);
        } else if (id == R.id.action_redo) {
            binding.code.onTextContextMenuItem(android.R.id.redo);
        } else if (id == R.id.action_find) {
            toggleFindBar(true);
        } else if (id == R.id.action_wrap) {
            wrap = !wrap;
            preferences.put(KEY_WRAP, wrap ? "1" : "0");
            applyWrap();
            refreshMenu();
        } else if (id == R.id.action_goto_line) {
            askForLine();
        } else if (id == R.id.action_save_and_commit) {
            save(false, true);
        } else if (id == R.id.action_blame) {
            openBlame();
        } else if (id == R.id.action_open_external) {
            if (!ExternalFiles.open(requireContext(), viewModel.file())) {
                Snackbar.make(binding.getRoot(), R.string.files_no_app, Snackbar.LENGTH_LONG).show();
            }
        } else if (id == R.id.action_share) {
            if (!ExternalFiles.share(requireContext(), viewModel.file())) {
                Snackbar.make(binding.getRoot(), R.string.files_no_app, Snackbar.LENGTH_LONG).show();
            }
        } else {
            return false;
        }
        return true;
    }

    /** Ohne Umbruch liegt das Textfeld in einem waagerecht scrollbaren Container, mit Umbruch direkt im Halter. */
    private void applyWrap() {
        final CodeEditText code = binding.code;
        final android.widget.FrameLayout holder = binding.textHolder;
        holder.removeAllViews();
        horizontalHolder.removeAllViews();
        code.setHorizontallyScrolling(!wrap);
        if (wrap) {
            holder.addView(code, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            horizontalHolder.addView(code, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            holder.addView(horizontalHolder, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (syntax != null) {
            syntax.viewChanged();
        }
    }

    private void askForLine() {
        final DialogNewFolderBinding dialogBinding = DialogNewFolderBinding.inflate(getLayoutInflater());
        dialogBinding.nameLayout.setHint(getString(R.string.viewer_line_hint));
        dialogBinding.nameInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.viewer_goto_line)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.viewer_goto, (shown, which) -> {
                    try {
                        gotoLine(Integer.parseInt(String.valueOf(dialogBinding.nameInput.getText()).trim()));
                    } catch (final NumberFormatException notANumber) {
                        // Keine Zahl: nichts zu tun.
                    }
                })
                .setNegativeButton(R.string.activity_cancel, null)
                .create();
        dialog.show();
    }

    private void gotoLine(
            final int line
    ) {
        final Editable text = binding.code.getText();
        if (text == null || line < 1) {
            return;
        }
        // Einmal kopieren: toString() je Zeile machte den Sprung in einer großen Datei quadratisch langsam.
        final String content = text.toString();
        int offset = 0;
        for (int current = 1; current < line; current += 1) {
            final int next = content.indexOf('\n', offset);
            if (next < 0) {
                break;
            }
            offset = next + 1;
        }
        binding.code.setSelection(offset);
        revealOffset(offset);
    }

    private void revealOffset(
            final int offset
    ) {
        final Layout layout = binding.code.getLayout();
        if (layout == null) {
            return;
        }
        final int y = layout.getLineTop(layout.getLineForOffset(offset)) + binding.code.getTotalPaddingTop();
        final NestedScrollView scroll = binding.textScroll;
        scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, y - scroll.getHeight() / 3)));
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Suchen und Ersetzen                                                                        */
    /* ------------------------------------------------------------------------------------------ */

    private void setupFind() {
        binding.findInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    final CharSequence text,
                    final int start,
                    final int count,
                    final int after
            ) {
                // nichts vorzubereiten
            }

            @Override
            public void onTextChanged(
                    final CharSequence text,
                    final int start,
                    final int before,
                    final int count
            ) {
                runSearch(true);
            }

            @Override
            public void afterTextChanged(
                    final Editable editable
            ) {
                // nichts nachzuarbeiten
            }
        });
        binding.findInput.setOnEditorActionListener((view, actionId, event) -> {
            step(1);
            return true;
        });
        binding.findNext.setOnClickListener(button -> step(1));
        binding.findPrevious.setOnClickListener(button -> step(-1));
        binding.findClose.setOnClickListener(button -> toggleFindBar(false));
        binding.replaceOne.setOnClickListener(button -> replaceCurrent());
        binding.replaceAll.setOnClickListener(button -> replaceAll());
    }

    private void toggleFindBar(
            final boolean show
    ) {
        binding.findBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.replaceRow.setVisibility(show && editing ? View.VISIBLE : View.GONE);
        if (show) {
            binding.findInput.requestFocus();
            final InputMethodManager keyboard = requireContext().getSystemService(InputMethodManager.class);
            keyboard.showSoftInput(binding.findInput, InputMethodManager.SHOW_IMPLICIT);
        } else {
            clearHighlights();
            matches.clear();
            hideKeyboard();
        }
    }

    private String query() {
        return String.valueOf(binding.findInput.getText());
    }

    private void runSearch(
            final boolean jumpToFirst
    ) {
        matches.clear();
        currentMatch = -1;
        clearHighlights();
        final String needle = query();
        final Editable text = binding.code.getText();
        if (needle.isEmpty() || text == null) {
            binding.findCount.setText("");
            return;
        }
        // Ein Muster statt toLowerCase: Umwandlungen wie „İ“ ändern die Länge des Textes, die Treffer lägen dann an der
        // falschen Stelle, und „Alle ersetzen“ risse fremde Zeichen mit.
        final Matcher matcher = Pattern.compile(Pattern.quote(needle), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(text);
        while (matches.size() < MAX_MATCHES && matcher.find()) {
            matches.add(matcher.start());
        }
        highlightMatches();
        if (matches.isEmpty()) {
            binding.findCount.setText(R.string.viewer_find_none);
        } else if (jumpToFirst) {
            final int caret = Math.max(0, binding.code.getSelectionStart());
            int first = 0;
            while (first < matches.size() - 1 && matches.get(first) < caret) {
                first += 1;
            }
            select(first);
        }
    }

    private void step(
            final int direction
    ) {
        if (matches.isEmpty()) {
            runSearch(true);
            return;
        }
        select((currentMatch + direction + matches.size()) % matches.size());
    }

    private void select(
            final int index
    ) {
        final Editable text = binding.code.getText();
        if (text == null) {
            return;
        }
        currentMatch = index;
        highlightMatches();
        final int start = matches.get(index);
        final int end = start + query().length();
        final int primary = MaterialColors.getColor(binding.code, androidx.appcompat.R.attr.colorPrimary);
        final int onPrimary = MaterialColors.getColor(binding.code, com.google.android.material.R.attr.colorOnPrimary);
        text.setSpan(new BackgroundColorSpan(primary), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new MatchTextSpan(onPrimary), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.findCount.setText(getString(R.string.viewer_find_count, index + 1, matches.size()));
        revealOffset(start);
    }

    /** Hebt alle Treffer sanft hervor; der aktuelle bekommt danach die kräftige Farbe. */
    private void highlightMatches() {
        final Editable text = binding.code.getText();
        if (text == null) {
            return;
        }
        clearHighlights();
        final int background = MaterialColors.getColor(binding.code, com.google.android.material.R.attr.colorSecondaryContainer);
        final int length = query().length();
        for (final int start : matches) {
            text.setSpan(new BackgroundColorSpan(background), start, start + length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void clearHighlights() {
        final Editable text = binding.code.getText();
        if (text == null) {
            return;
        }
        for (final BackgroundColorSpan span : text.getSpans(0, text.length(), BackgroundColorSpan.class)) {
            text.removeSpan(span);
        }
        for (final MatchTextSpan span : text.getSpans(0, text.length(), MatchTextSpan.class)) {
            text.removeSpan(span);
        }
    }

    /** Textfarbe des aktuellen Suchtreffers; eigene Klasse, damit das Entfernen die Syntaxfärbung nicht mitnimmt. */
    private static final class MatchTextSpan extends ForegroundColorSpan {

        private MatchTextSpan(
                final int color
        ) {
            super(color);
        }
    }

    private void replaceCurrent() {
        final Editable text = binding.code.getText();
        if (!editing || text == null || currentMatch < 0 || currentMatch >= matches.size()) {
            return;
        }
        final int start = matches.get(currentMatch);
        text.replace(start, start + query().length(), String.valueOf(binding.replaceInput.getText()));
        runSearch(true);
    }

    private void replaceAll() {
        final Editable text = binding.code.getText();
        if (!editing || text == null || query().isEmpty()) {
            return;
        }
        clearHighlights();
        final String replacement = String.valueOf(binding.replaceInput.getText());
        // Von hinten nach vorn, damit die Positionen der übrigen Treffer stimmen bleiben.
        runSearch(false);
        final int length = query().length();
        for (int index = matches.size() - 1; index >= 0; index -= 1) {
            final int start = matches.get(index);
            text.replace(start, start + length, replacement);
        }
        runSearch(false);
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Speichern                                                                                  */
    /* ------------------------------------------------------------------------------------------ */

    /** Erinnert sich, ob nach dem Speichern der Commit-Dialog des Repos folgen soll. */
    private boolean commitAfterSave;

    private void save(
            final boolean force,
            final boolean thenCommit
    ) {
        commitAfterSave = thenCommit;
        clearHighlights();
        viewModel.save(String.valueOf(binding.code.getText()), force);
    }

    private void onSaveOutcome(
            @Nullable final Event<FileViewerViewModel.SaveOutcome> event
    ) {
        final FileViewerViewModel.SaveOutcome outcome = event == null ? null : event.consume();
        if (outcome == null) {
            return;
        }
        switch (outcome) {
            case SAVED:
                dirty = false;
                refreshMenu();
                Snackbar.make(binding.getRoot(), R.string.viewer_saved, Snackbar.LENGTH_SHORT).show();
                if (commitAfterSave) {
                    commitAfterSave = false;
                    requestCommit();
                }
                break;
            case CHANGED_ON_DISK:
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.viewer_changed_title)
                        .setMessage(R.string.viewer_changed_body)
                        .setPositiveButton(R.string.viewer_overwrite, (dialog, which) -> save(true, commitAfterSave))
                        .setNegativeButton(R.string.activity_cancel, null)
                        .show();
                break;
            case FAILED:
            default:
                commitAfterSave = false;
                Snackbar.make(binding.getRoot(), R.string.viewer_save_failed, Snackbar.LENGTH_LONG).show();
                break;
        }
    }

    /** Merkt die Datei vor, geht zum Repo zurück und lässt dort den Commit-Dialog öffnen. */
    private void requestCommit() {
        final Context app = requireContext().getApplicationContext();
        de.lembergmax.gitmax.ServiceLocator.from(app).io().execute(() -> {
            try {
                de.lembergmax.gitmax.ServiceLocator.from(app).engine().stage(
                        new File(requireArguments().getString(ARG_DIRECTORY, "")), List.of(viewModel.path()));
            } catch (final de.lembergmax.gitmax.domain.GitFailureException notStaged) {
                // Der Commit-Dialog zeigt die Datei ohnehin als „geändert“; das Vormerken ist nur eine Hilfe.
            }
        });
        getParentFragmentManager().setFragmentResult(RepoDetailFragment.REQUEST_COMMIT_KEY, new Bundle());
        NavHostFragment.findNavController(this).popBackStack(R.id.repoDetailFragment, false);
    }

    private void confirmLeave() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.viewer_unsaved_title)
                .setMessage(R.string.viewer_unsaved_body)
                .setPositiveButton(R.string.viewer_unsaved_save, (dialog, which) -> save(false, false))
                .setNegativeButton(R.string.viewer_unsaved_discard, (dialog, which) -> {
                    dirty = false;
                    leaveGuard.setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                })
                .setNeutralButton(R.string.viewer_unsaved_cancel, null)
                .show();
    }
}
