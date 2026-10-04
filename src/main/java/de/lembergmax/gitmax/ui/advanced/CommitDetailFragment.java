package de.lembergmax.gitmax.ui.advanced;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.FragmentSimpleListBinding;
import de.lembergmax.gitmax.domain.model.CommitDetail;
import de.lembergmax.gitmax.domain.model.FileChange;
import de.lembergmax.gitmax.domain.model.ResetMode;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;

import java.util.List;

/**
 * Ein Commit im Detail mit den Aktionen: Kennung kopieren, auschecken, Branch oder Tag hier anlegen,
 * übernehmen, rückgängig machen und den Branch hierher zurücksetzen.
 */
public final class CommitDetailFragment extends Fragment {

    /** Pfad des Repos. */
    public static final String ARG_DIRECTORY = "directory";

    /** Kennung des Commits. */
    public static final String ARG_COMMIT = "commit";

    private static final int SHORT_ID_LENGTH = 7;

    private FragmentSimpleListBinding binding;
    private CommitDetailViewModel viewModel;
    private CommitDetailAdapter adapter;

    public CommitDetailFragment() {
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
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentSimpleListBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(CommitDetailViewModel.class);
        final String directory = requireArguments().getString(ARG_DIRECTORY);
        final String commit = requireArguments().getString(ARG_COMMIT);
        if (directory == null || commit == null) {
            throw new IllegalStateException("No commit passed");
        }
        viewModel.init(directory, commit);

        Toolbars.setupBack(this, binding.toolbar);
        binding.toolbar.setTitle(R.string.commit_detail_title);
        binding.toolbar.getMenu().findItem(R.id.action_create).setVisible(false);
        binding.toolbar.inflateMenu(R.menu.menu_commit);
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItem);

        adapter = new CommitDetailAdapter(this::openDiff);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);

        viewModel.detail().observe(getViewLifecycleOwner(), this::render);
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

    private void render(
            @Nullable final CommitDetail detail
    ) {
        if (detail != null) {
            adapter.submit(detail);
            binding.toolbar.setSubtitle(detail.info().shortId());
        }
    }

    private boolean onMenuItem(
            @NonNull final MenuItem item
    ) {
        final int id = item.getItemId();
        if (id == R.id.action_copy_hash) {
            final ClipboardManager clipboard = requireContext().getSystemService(ClipboardManager.class);
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.commit_copy_hash), viewModel.commitId()));
            Snackbar.make(binding.getRoot(), R.string.commit_hash_copied, Snackbar.LENGTH_SHORT).show();
        } else if (id == R.id.action_checkout) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.commit_checkout)
                    .setMessage(R.string.commit_checkout_body)
                    .setPositiveButton(R.string.commit_checkout, (dialog, which) -> viewModel.checkout(R.string.commit_checked_out))
                    .setNegativeButton(R.string.activity_cancel, null)
                    .show();
        } else if (id == R.id.action_branch_here) {
            PromptDialog.show(requireContext(), getLayoutInflater(), new ListSource.Prompt(R.string.commit_branch_here, R.string.files_create,
                    List.of(new ListSource.Field(R.string.branches_name_hint, "", false, true)),
                    List.of(new ListSource.Toggle(R.string.branches_checkout_toggle, true))),
                    values -> viewModel.createBranch(values.field(0).strip(), values.toggle(0), R.string.commit_branch_created));
        } else if (id == R.id.action_tag_here) {
            PromptDialog.show(requireContext(), getLayoutInflater(), new ListSource.Prompt(R.string.commit_tag_here, R.string.files_create,
                    List.of(new ListSource.Field(R.string.tags_name_hint, "", false, true),
                            new ListSource.Field(R.string.tags_message_hint, "", true, false)),
                    List.of()),
                    values -> viewModel.createTag(values.field(0).strip(), values.field(1), R.string.commit_tag_created));
        } else if (id == R.id.action_cherry_pick) {
            confirm(R.string.commit_cherry_pick, getString(R.string.commit_cherry_pick_body), R.string.commit_cherry_pick,
                    () -> viewModel.cherryPick(R.string.commit_cherry_picked));
        } else if (id == R.id.action_revert) {
            confirm(R.string.commit_revert, getString(R.string.commit_revert_body), R.string.commit_revert,
                    () -> viewModel.revert(R.string.commit_reverted));
        } else if (id == R.id.action_reset) {
            chooseResetMode();
        } else {
            return false;
        }
        return true;
    }

    private void confirm(
            final int title,
            @NonNull final String body,
            final int confirmLabel,
            @NonNull final Runnable action
    ) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton(confirmLabel, (dialog, which) -> action.run())
                .setNegativeButton(R.string.activity_cancel, null)
                .show();
    }

    /** Fragt, wie weit das Zurücksetzen reichen soll; „hart“ verlangt eine zweite Bestätigung. */
    private void chooseResetMode() {
        final ResetMode[] modes = {ResetMode.SOFT, ResetMode.MIXED, ResetMode.HARD};
        final String[] labels = {
                getString(R.string.commit_reset_soft),
                getString(R.string.commit_reset_mixed),
                getString(R.string.commit_reset_hard)
        };
        final int[] chosen = {1};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.commit_reset_title, shortCommit()))
                .setSingleChoiceItems(labels, chosen[0], (dialog, which) -> chosen[0] = which)
                .setPositiveButton(R.string.commit_reset_confirm, (dialog, which) -> {
                    if (modes[chosen[0]] == ResetMode.HARD) {
                        confirm(R.string.commit_reset_hard_title, getString(R.string.commit_reset_hard_body), R.string.commit_reset_confirm,
                                () -> viewModel.reset(ResetMode.HARD, R.string.commit_reset_done));
                    } else {
                        viewModel.reset(modes[chosen[0]], R.string.commit_reset_done);
                    }
                })
                .setNegativeButton(R.string.activity_cancel, null)
                .show();
    }

    private String shortCommit() {
        final String id = viewModel.commitId();
        return id.length() > SHORT_ID_LENGTH ? id.substring(0, SHORT_ID_LENGTH) : id;
    }

    private void openConflicts() {
        final Bundle arguments = new Bundle();
        arguments.putString(ConflictsFragment.ARG_DIRECTORY, viewModel.repo().getAbsolutePath());
        NavHostFragment.findNavController(this).navigate(R.id.conflictsFragment, arguments);
    }

    private void openDiff(
            @NonNull final FileChange file
    ) {
        final Bundle arguments = new Bundle();
        arguments.putString(DiffFragment.ARG_DIRECTORY, viewModel.repo().getAbsolutePath());
        arguments.putString(DiffFragment.ARG_MODE, DiffViewModel.Mode.COMMIT.name());
        arguments.putString(DiffFragment.ARG_COMMIT, viewModel.commitId());
        arguments.putString(DiffFragment.ARG_PATH, file.path());
        NavHostFragment.findNavController(this).navigate(R.id.diffFragment, arguments);
    }

    private void showMessage(
            @Nullable final Event<CommitDetailViewModel.Message> event
    ) {
        final CommitDetailViewModel.Message message = event == null ? null : event.consume();
        if (message == null) {
            return;
        }
        final Snackbar snackbar = Snackbar.make(binding.getRoot(), message.text(),
                message.conflict() ? Snackbar.LENGTH_INDEFINITE : Snackbar.LENGTH_LONG);
        if (message.conflict()) {
            snackbar.setAction(R.string.conflicts_open, view -> openConflicts());
        }
        snackbar.show();
    }
}
