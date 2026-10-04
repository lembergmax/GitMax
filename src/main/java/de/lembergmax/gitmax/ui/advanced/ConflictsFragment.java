package de.lembergmax.gitmax.ui.advanced;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.FragmentConflictsBinding;
import de.lembergmax.gitmax.domain.model.ConflictFile;
import de.lembergmax.gitmax.domain.model.ConflictKind;
import de.lembergmax.gitmax.domain.model.Resolution;
import de.lembergmax.gitmax.domain.model.RunningOperation;
import de.lembergmax.gitmax.ui.common.AdaptiveRow;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.InsetsPadding;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;
import de.lembergmax.gitmax.ui.viewer.FileViewerFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Löst die Konflikte eines angefangenen Merges, Rebases, Cherry-picks oder Reverts: je Datei die eigene,
 * die andere oder beide Seiten übernehmen oder von Hand bearbeiten, dann den Vorgang fortsetzen,
 * überspringen oder abbrechen.
 */
public final class ConflictsFragment extends Fragment {

    /** Pfad des Repos. */
    public static final String ARG_DIRECTORY = "directory";

    /** Schlüssel des Fragment-Ergebnisses, wenn der Vorgang zu Ende ist. */
    public static final String RESULT_KEY = "conflicts_finished";

    /** Text der Abschlussmeldung im Ergebnis. */
    public static final String RESULT_TEXT = "text";

    private static final int MENU_OURS = 1;
    private static final int MENU_THEIRS = 2;
    private static final int MENU_BOTH = 3;
    private static final int MENU_EDIT = 4;
    private static final int MENU_RESOLVED = 5;

    private FragmentConflictsBinding binding;
    private ConflictsViewModel viewModel;
    private SimpleRowAdapter adapter;

    public ConflictsFragment() {
        super(R.layout.fragment_conflicts);
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
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentConflictsBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(ConflictsViewModel.class);
        final String directory = requireArguments().getString(ARG_DIRECTORY);
        if (directory == null) {
            throw new IllegalStateException("No repo path passed");
        }
        viewModel.init(directory);

        Toolbars.setupBack(this, binding.toolbar);
        InsetsPadding.apply(binding.bar, false, true, false);
        adapter = new SimpleRowAdapter(this::showActions, true);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null);

        binding.continueButton.setOnClickListener(button -> viewModel.continueOperation());
        binding.skipButton.setOnClickListener(button -> confirmSkip());
        binding.abortButton.setOnClickListener(button -> confirmAbort());

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.notices().observe(getViewLifecycleOwner(), this::showNotice);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.reload();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        adapter = null;
        super.onDestroyView();
    }

    private void render(
            @NonNull final ConflictsViewModel.UiState state
    ) {
        final boolean running = state.operation() != RunningOperation.NONE;
        binding.progress.setVisibility(state.loading() || state.busy() ? View.VISIBLE : View.INVISIBLE);

        binding.statusCard.setVisibility(running ? View.VISIBLE : View.GONE);
        binding.statusTitle.setText(ConflictTexts.running(requireContext(), state.operation()));
        binding.statusBody.setText(state.files().isEmpty()
                ? getString(R.string.conflicts_all_resolved)
                : getResources().getQuantityString(R.plurals.conflicts_open, state.files().size(), state.files().size()));

        final List<SimpleRow> rows = new ArrayList<>();
        for (final ConflictFile file : state.files()) {
            rows.add(rowOf(file, state.operation()));
        }
        adapter.submitList(rows);
        final boolean empty = rows.isEmpty() && !running && !state.loading();
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(empty ? View.GONE : View.VISIBLE);

        binding.bar.setVisibility(running ? View.VISIBLE : View.GONE);
        binding.continueButton.setEnabled(running && state.files().isEmpty() && !state.busy());
        binding.skipButton.setVisibility(state.operation() == RunningOperation.REBASE ? View.VISIBLE : View.GONE);
        AdaptiveRow.apply(binding.secondaryActions);
        binding.skipButton.setEnabled(!state.busy());
        binding.abortButton.setEnabled(!state.busy());
    }

    private SimpleRow rowOf(
            @NonNull final ConflictFile file,
            @NonNull final RunningOperation operation
    ) {
        final String kind = getString(ConflictTexts.kind(file.kind(), operation));
        final boolean editable = isEditable(file);
        final String hint = !editable ? "" : getString(file.hasMarkers() ? R.string.conflicts_row_markers : R.string.conflicts_row_no_markers);
        return new SimpleRow(file.path(), file.path(), hint.isEmpty() ? kind : kind + " · " + hint,
                R.drawable.ic_warning, null, false, file);
    }

    /** Nur Dateien, die es auf beiden Seiten gibt, lassen sich von Hand bearbeiten. */
    private static boolean isEditable(
            @NonNull final ConflictFile file
    ) {
        return file.kind() == ConflictKind.BOTH_MODIFIED || file.kind() == ConflictKind.BOTH_ADDED;
    }

    private void showActions(
            @NonNull final View anchor,
            @NonNull final SimpleRow row
    ) {
        final ConflictFile file = (ConflictFile) row.payload();
        final ConflictsViewModel.UiState state = viewModel.state().getValue();
        if (file == null || state == null || state.busy()) {
            return;
        }
        final RunningOperation operation = state.operation();
        final PopupMenu menu = new PopupMenu(requireContext(), anchor);
        if (file.kind() != ConflictKind.BOTH_DELETED) {
            menu.getMenu().add(0, MENU_OURS, 0, ConflictTexts.ours(operation));
            menu.getMenu().add(0, MENU_THEIRS, 1, ConflictTexts.theirs(operation));
        }
        if (isEditable(file) && file.hasMarkers()) {
            menu.getMenu().add(0, MENU_BOTH, 2, R.string.conflicts_keep_both);
        }
        if (isEditable(file)) {
            menu.getMenu().add(0, MENU_EDIT, 3, R.string.conflicts_edit);
        }
        menu.getMenu().add(0, MENU_RESOLVED, 4, R.string.conflicts_mark_resolved);
        menu.setOnMenuItemClickListener(item -> {
            perform(file, item.getItemId());
            return true;
        });
        menu.show();
    }

    private void perform(
            @NonNull final ConflictFile file,
            final int action
    ) {
        switch (action) {
            case MENU_OURS:
                viewModel.resolve(file, Resolution.OURS);
                break;
            case MENU_THEIRS:
                viewModel.resolve(file, Resolution.THEIRS);
                break;
            case MENU_BOTH:
                viewModel.resolve(file, Resolution.BOTH);
                break;
            case MENU_EDIT:
                edit(file);
                break;
            case MENU_RESOLVED:
            default:
                viewModel.markResolved(file);
                break;
        }
    }

    private void edit(
            @NonNull final ConflictFile file
    ) {
        final Bundle arguments = new Bundle();
        arguments.putString(FileViewerFragment.ARG_DIRECTORY, viewModel.repo().getAbsolutePath());
        arguments.putString(FileViewerFragment.ARG_PATH, file.path());
        arguments.putBoolean(FileViewerFragment.ARG_EDIT, true);
        NavHostFragment.findNavController(this).navigate(R.id.fileViewerFragment, arguments);
    }

    private void confirmAbort() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.conflicts_abort_title)
                .setMessage(R.string.conflicts_abort_body)
                .setPositiveButton(R.string.conflicts_abort_confirm, (dialog, which) -> viewModel.abort())
                .setNegativeButton(R.string.conflicts_keep_solving, null)
                .show();
    }

    private void confirmSkip() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.conflicts_skip_title)
                .setMessage(R.string.conflicts_skip_body)
                .setPositiveButton(R.string.conflicts_skip_confirm, (dialog, which) -> viewModel.skip())
                .setNegativeButton(R.string.conflicts_keep_solving, null)
                .show();
    }

    private void showNotice(
            @Nullable final Event<ConflictsViewModel.Notice> event
    ) {
        final ConflictsViewModel.Notice notice = event == null ? null : event.consume();
        if (notice == null) {
            return;
        }
        switch (notice.kind()) {
            case FINISHED:
                finish(notice.text());
                break;
            case ERROR:
                Snackbar.make(binding.getRoot(), notice.text(), Snackbar.LENGTH_LONG).show();
                break;
            case INFO:
            default:
                Snackbar.make(binding.getRoot(), notice.text(), Snackbar.LENGTH_SHORT).show();
                break;
        }
    }

    /** Geht zum Repo zurück und lässt es die Abschlussmeldung zeigen. */
    private void finish(
            @NonNull final String text
    ) {
        final Bundle result = new Bundle();
        result.putString(RESULT_TEXT, text);
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
        if (!NavHostFragment.findNavController(this).popBackStack(R.id.repoDetailFragment, false)) {
            NavHostFragment.findNavController(this).navigateUp();
        }
    }
}
