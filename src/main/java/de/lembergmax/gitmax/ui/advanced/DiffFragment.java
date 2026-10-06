package de.lembergmax.gitmax.ui.advanced;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.FragmentSimpleListBinding;
import de.lembergmax.gitmax.domain.model.DiffHunk;
import de.lembergmax.gitmax.domain.model.DiffLine;
import de.lembergmax.gitmax.domain.model.FileDiff;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;

/** Der Diff einer Datei, Zeile für Zeile; ab 600 dp Breite auch nebeneinander. */
public final class DiffFragment extends Fragment {

    /** Pfad des Repos. */
    public static final String ARG_DIRECTORY = "directory";

    /** Woher der Diff kommt, siehe {@link DiffViewModel.Mode}. */
    public static final String ARG_MODE = "mode";

    /** Kennung des Commits bei {@link DiffViewModel.Mode#COMMIT}. */
    public static final String ARG_COMMIT = "commit";

    /** Pfad der Datei im Repo. */
    public static final String ARG_PATH = "path";

    private static final int WIDE_DP = 600;

    private FragmentSimpleListBinding binding;
    private DiffViewModel viewModel;
    private DiffAdapter adapter;
    private boolean sideBySide;

    public DiffFragment() {
        super(R.layout.fragment_simple_list);
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
        sideBySide = requireContext().getResources().getConfiguration().screenWidthDp >= WIDE_DP;
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentSimpleListBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(DiffViewModel.class);
        final String directory = requireArguments().getString(ARG_DIRECTORY);
        final String path = requireArguments().getString(ARG_PATH);
        final String mode = requireArguments().getString(ARG_MODE);
        if (directory == null || path == null || mode == null) {
            throw new IllegalStateException("No diff specified");
        }
        viewModel.init(directory, DiffViewModel.Mode.valueOf(mode), requireArguments().getString(ARG_COMMIT), path);

        Toolbars.setupBack(this, binding.toolbar);
        binding.toolbar.setTitle(path.substring(path.lastIndexOf('/') + 1));
        binding.toolbar.setSubtitle(path);
        binding.toolbar.getMenu().findItem(R.id.action_create).setVisible(false);
        binding.toolbar.inflateMenu(R.menu.menu_diff);
        final Menu menu = binding.toolbar.getMenu();
        final boolean wide = requireContext().getResources().getConfiguration().screenWidthDp >= WIDE_DP;
        menu.findItem(R.id.action_side_by_side).setVisible(wide);
        menu.findItem(R.id.action_side_by_side).setChecked(sideBySide);
        // Das ViewModel überlebt die Drehung, das neu aufgebaute Menü nicht: ohne das stünde der Haken falsch.
        menu.findItem(R.id.action_ignore_whitespace).setChecked(viewModel.ignoreWhitespace());
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItem);

        adapter = new DiffAdapter();
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null);

        viewModel.diff().observe(getViewLifecycleOwner(), this::render);
        viewModel.loading().observe(getViewLifecycleOwner(), loading ->
                binding.progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.INVISIBLE));
        viewModel.messages().observe(getViewLifecycleOwner(), this::showMessage);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        adapter = null;
        super.onDestroyView();
    }

    private boolean onMenuItem(
            @NonNull final MenuItem item
    ) {
        if (item.getItemId() == R.id.action_ignore_whitespace) {
            item.setChecked(!item.isChecked());
            viewModel.setIgnoreWhitespace(item.isChecked());
            return true;
        }
        if (item.getItemId() == R.id.action_side_by_side) {
            item.setChecked(!item.isChecked());
            sideBySide = item.isChecked();
            final FileDiff diff = viewModel.diff().getValue();
            if (diff != null) {
                adapter.submit(diff, sideBySide);
            }
            return true;
        }
        return false;
    }

    private void render(
            @Nullable final FileDiff diff
    ) {
        if (diff == null) {
            return;
        }
        adapter.submit(diff, sideBySide);
        final boolean noLines = diff.hunks().isEmpty();
        binding.empty.setVisibility(noLines ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(noLines ? View.GONE : View.VISIBLE);
        if (diff.binary()) {
            binding.emptyTitle.setText(R.string.diff_binary_title);
            binding.emptyBody.setText(R.string.diff_binary_body);
        } else if (diff.tooLarge()) {
            binding.emptyTitle.setText(R.string.diff_too_large_title);
            binding.emptyBody.setText(R.string.diff_too_large_body);
        } else {
            binding.emptyTitle.setText(R.string.diff_empty_title);
            binding.emptyBody.setText(R.string.diff_empty_body);
        }
        int added = 0;
        int removed = 0;
        for (final DiffHunk hunk : diff.hunks()) {
            for (final DiffLine line : hunk.lines()) {
                added += line.type() == DiffLine.Type.ADDED ? 1 : 0;
                removed += line.type() == DiffLine.Type.REMOVED ? 1 : 0;
            }
        }
        if (!noLines) {
            binding.toolbar.setSubtitle("+" + added + " −" + removed + " · " + diff.path());
        }
    }

    private void showMessage(
            @Nullable final Event<String> event
    ) {
        final String message = event == null ? null : event.consume();
        if (message != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }
}
