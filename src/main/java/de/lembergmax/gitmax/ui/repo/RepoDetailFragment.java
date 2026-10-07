package de.lembergmax.gitmax.ui.repo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.MenuItem;
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
import de.lembergmax.gitmax.databinding.FragmentRepoDetailBinding;
import de.lembergmax.gitmax.domain.model.ChangedFile;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.ops.OperationEntry;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.HostKeyDialog;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.TypedConfirmDialog;
import de.lembergmax.gitmax.ui.common.Toolbars;
import de.lembergmax.gitmax.ui.advanced.AdvancedFragment;
import de.lembergmax.gitmax.ui.advanced.ConflictsFragment;
import de.lembergmax.gitmax.ui.advanced.DiffFragment;
import de.lembergmax.gitmax.ui.advanced.DiffViewModel;
import de.lembergmax.gitmax.ui.files.FilesFragment;

import java.util.List;

/**
 * Ein Repo im Detail: Zustand, die drei Hauptaktionen (Aktualisieren, Committen, Pushen) und die Liste der
 * Änderungen mit Vormerken und Verwerfen.
 */
public final class RepoDetailFragment extends Fragment {

    /** Pfad des Repos als Argument. */
    public static final String ARG_DIRECTORY = "directory";

    /** Ergebnis-Schlüssel: das Repo wurde vom Gerät gelöscht; „Lokal“ meldet es. */
    public static final String RESULT_DELETED_KEY = "repo_deleted";

    /** Ergebnis-Schlüssel: der Dateieditor bittet darum, den Commit-Dialog zu öffnen. */
    public static final String REQUEST_COMMIT_KEY = "request_commit";

    private FragmentRepoDetailBinding binding;
    private RepoDetailViewModel viewModel;
    private RepoDetailAdapter adapter;

    public RepoDetailFragment() {
        super(R.layout.fragment_repo_detail);
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
        binding = FragmentRepoDetailBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(RepoDetailViewModel.class);
        final String directory = requireArguments().getString(ARG_DIRECTORY);
        if (directory == null) {
            throw new IllegalStateException("No repo path passed");
        }
        viewModel.init(directory);

        Toolbars.setupBack(this, binding.toolbar);
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItem);

        adapter = new RepoDetailAdapter(new Actions());
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null);

        getChildFragmentManager().setFragmentResultListener(CommitSheetFragment.RESULT_KEY, getViewLifecycleOwner(),
                (key, result) -> Snackbar.make(binding.getRoot(),
                        getString(R.string.commit_done, result.getString(CommitSheetFragment.RESULT_ID, "")),
                        Snackbar.LENGTH_LONG).show());

        getParentFragmentManager().setFragmentResultListener(REQUEST_COMMIT_KEY, getViewLifecycleOwner(),
                (key, result) -> CommitSheetFragment.show(getChildFragmentManager()));

        getParentFragmentManager().setFragmentResultListener(ConflictsFragment.RESULT_KEY, getViewLifecycleOwner(),
                (key, result) -> Snackbar.make(binding.getRoot(),
                        result.getString(ConflictsFragment.RESULT_TEXT, ""), Snackbar.LENGTH_LONG).show());

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.messages().observe(getViewLifecycleOwner(), this::showMessage);
        viewModel.deletions().observe(getViewLifecycleOwner(), event -> {
            if (event != null && event.consume() != null) {
                getParentFragmentManager().setFragmentResult(RESULT_DELETED_KEY, new Bundle());
                NavHostFragment.findNavController(this).popBackStack();
            }
        });
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
            @NonNull final RepoDetailViewModel.UiState state
    ) {
        binding.toolbar.setTitle(state.name());
        binding.progress.setVisibility(state.loading() ? View.VISIBLE : View.INVISIBLE);
        binding.unreadable.setVisibility(state.unreadable() ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(state.unreadable() ? View.GONE : View.VISIBLE);
        final boolean inProgress = state.tree() != null && state.tree().inProgress();
        binding.toolbar.getMenu().findItem(R.id.action_resolve_conflicts).setVisible(inProgress);
        binding.toolbar.getMenu().findItem(R.id.action_abort_merge).setVisible(inProgress);
        if (!state.unreadable()) {
            adapter.submitList(RepoDetailAdapter.itemsFor(state));
        }
    }

    private boolean onMenuItem(
            @NonNull final MenuItem item
    ) {
        final int id = item.getItemId();
        if (id == R.id.action_files) {
            final Bundle arguments = new Bundle();
            arguments.putString(FilesFragment.ARG_DIRECTORY, viewModel.directory().getAbsolutePath());
            NavHostFragment.findNavController(this).navigate(R.id.filesFragment, arguments);
        } else if (id == R.id.action_advanced) {
            final Bundle arguments = new Bundle();
            arguments.putString(AdvancedFragment.ARG_DIRECTORY, viewModel.directory().getAbsolutePath());
            NavHostFragment.findNavController(this).navigate(R.id.advancedFragment, arguments);
        } else if (id == R.id.action_fetch) {
            viewModel.fetch();
        } else if (id == R.id.action_resolve_conflicts) {
            openConflicts();
        } else if (id == R.id.action_abort_merge) {
            confirmAbort();
        } else if (id == R.id.action_force_push) {
            confirmForcePush();
        } else if (id == R.id.action_delete_repo) {
            confirmDelete();
        } else if (id == R.id.action_copy_path) {
            final ClipboardManager clipboard = requireContext().getSystemService(ClipboardManager.class);
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.repo_menu_copy_path),
                    viewModel.directory().getAbsolutePath()));
            Snackbar.make(binding.getRoot(), R.string.repo_path_copied, Snackbar.LENGTH_SHORT).show();
        } else {
            return false;
        }
        return true;
    }

    private void openConflicts() {
        final Bundle arguments = new Bundle();
        arguments.putString(ConflictsFragment.ARG_DIRECTORY, viewModel.directory().getAbsolutePath());
        NavHostFragment.findNavController(this).navigate(R.id.conflictsFragment, arguments);
    }

    private void openDiff(
            @NonNull final RepoDetailAdapter.Section section,
            @NonNull final ChangedFile file
    ) {
        final Bundle arguments = new Bundle();
        arguments.putString(DiffFragment.ARG_DIRECTORY, viewModel.directory().getAbsolutePath());
        arguments.putString(DiffFragment.ARG_MODE, (section == RepoDetailAdapter.Section.STAGED
                ? DiffViewModel.Mode.STAGED : DiffViewModel.Mode.WORKTREE).name());
        arguments.putString(DiffFragment.ARG_PATH, file.path());
        NavHostFragment.findNavController(this).navigate(R.id.diffFragment, arguments);
    }

    private void confirmAbort() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.repo_abort_title)
                .setMessage(R.string.repo_abort_body)
                .setPositiveButton(R.string.repo_abort_confirm, (dialog, which) -> {
                    viewModel.abortMerge();
                    Snackbar.make(binding.getRoot(), R.string.repo_aborted, Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.repo_abort_keep, null)
                .show();
    }

    private void confirmForcePush() {
        TypedConfirmDialog.show(requireContext(), getLayoutInflater(), R.string.repo_force_push_title,
                getString(R.string.repo_force_push_body), viewModel.directory().getName(), R.string.repo_force_push_confirm, () -> {
                    viewModel.forcePush();
                    Snackbar.make(binding.getRoot(), R.string.repo_force_push_started, Snackbar.LENGTH_SHORT).show();
                });
    }

    private void confirmDelete() {
        final String name = viewModel.directory().getName();
        TypedConfirmDialog.show(requireContext(), getLayoutInflater(), R.string.repo_delete_title,
                getString(R.string.repo_delete_body, name), name, R.string.repo_delete_confirm, viewModel::deleteFromDevice);
    }

    private void confirmDiscard(
            @NonNull final ChangedFile file
    ) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.repo_discard_title)
                .setMessage(getString(R.string.repo_discard_body_one, file.path()))
                .setPositiveButton(R.string.repo_discard_confirm, (dialog, which) -> viewModel.discard(List.of(file.path())))
                .setNegativeButton(R.string.activity_cancel, null)
                .show();
    }

    private void showMessage(
            @Nullable final Event<String> event
    ) {
        final String message = event == null ? null : event.consume();
        if (message != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }

    /** Verbindet die Bedienung der Liste mit dem ViewModel. */
    private final class Actions implements RepoDetailAdapter.Listener {

        @Override
        public void onUpdate() {
            viewModel.update(false);
        }

        @Override
        public void onCommit() {
            CommitSheetFragment.show(getChildFragmentManager());
        }

        @Override
        public void onPush() {
            if (!viewModel.push()) {
                Snackbar.make(binding.getRoot(), R.string.repo_nothing_to_push, Snackbar.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onFailureAction() {
            final RepoDetailViewModel.UiState state = viewModel.state().getValue();
            final OperationEntry failure = state == null ? null : state.failure();
            if (failure == null || failure.failure() == null) {
                return;
            }
            final GitFailureKind kind = failure.failure().kind();
            if (kind == GitFailureKind.HOST_KEY_UNKNOWN || kind == GitFailureKind.HOST_KEY_CHANGED) {
                final String host = failure.failure().paths().isEmpty() ? "" : failure.failure().paths().get(0);
                HostKeyDialog.show(requireContext(), viewModel.knownHosts(), host, binding.getRoot(), () -> {
                    viewModel.dismissFailure();
                    viewModel.retry(failure);
                });
                return;
            }
            viewModel.dismissFailure();
            if (kind == GitFailureKind.METERED_NETWORK) {
                viewModel.retry(failure);
                return;
            }
            if (kind == GitFailureKind.SSH_NO_KEY || kind == GitFailureKind.SSH_REJECTED) {
                NavHostFragment.findNavController(RepoDetailFragment.this).navigate(R.id.sshKeysFragment);
                return;
            }
            if (kind == GitFailureKind.CONFLICT) {
                openConflicts();
                return;
            }
            viewModel.update(failure.failure().kind() == GitFailureKind.DIRTY_TREE);
        }

        @Override
        public void onFailureDismiss() {
            viewModel.dismissFailure();
        }

        @Override
        public void onToggle(
                @NonNull final RepoDetailAdapter.Section section,
                @NonNull final ChangedFile file
        ) {
            viewModel.toggle(file, section == RepoDetailAdapter.Section.STAGED);
        }

        @Override
        public void onFileMenu(
                @NonNull final View anchor,
                @NonNull final RepoDetailAdapter.Section section,
                @NonNull final ChangedFile file
        ) {
            final PopupMenu menu = new PopupMenu(requireContext(), anchor);
            menu.getMenu().add(0, 1, 0, R.string.repo_file_diff);
            if (section != RepoDetailAdapter.Section.CONFLICTS) {
                menu.getMenu().add(0, 2, 1, R.string.repo_discard);
            }
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    openDiff(section, file);
                } else {
                    confirmDiscard(file);
                }
                return true;
            });
            menu.show();
        }

        @Override
        public void onSectionAction(
                @NonNull final RepoDetailAdapter.Section section,
                @NonNull final List<ChangedFile> files
        ) {
            if (section == RepoDetailAdapter.Section.CONFLICTS) {
                openConflicts();
            } else if (section == RepoDetailAdapter.Section.STAGED) {
                viewModel.unstageAll(files);
            } else {
                viewModel.stageAll(files);
            }
        }
    }
}
