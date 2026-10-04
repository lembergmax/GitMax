package de.lembergmax.gitmax.ui.local;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.FragmentLocalBinding;
import de.lembergmax.gitmax.storage.StoragePermission;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.NotificationGate;
import de.lembergmax.gitmax.ui.picker.FolderPickerFragment;
import de.lembergmax.gitmax.ui.repo.RepoDetailFragment;

import java.io.File;
import java.util.function.IntSupplier;

/**
 * Startbildschirm: die Repos in den Arbeitsordnern mit ihrem Zustand, Filtern und Suche. Ein Tipp öffnet
 * das Repo, langes Drücken beginnt die Auswahl für Sammelaktionen. Führt beim ersten Start durch die
 * Freigabe „Zugriff auf alle Dateien“ und die Wahl des Arbeitsordners.
 */
public final class LocalFragment extends Fragment {

    private FragmentLocalBinding binding;
    private LocalViewModel viewModel;
    private LocalRepoAdapter adapter;
    private NotificationGate notificationGate;
    private OnBackPressedCallback leaveSelection;
    private boolean updatingChips;

    public LocalFragment() {
        super(R.layout.fragment_local);
    }

    @Override
    public void onCreate(
            @Nullable final Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        notificationGate = new NotificationGate(this);
        setEnterTransition(Motion.fadeThrough(requireContext()));
        setExitTransition(Motion.fadeThrough(requireContext()));
        setReenterTransition(Motion.fadeThrough(requireContext()));
        setReturnTransition(Motion.fadeThrough(requireContext()));
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentLocalBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(LocalViewModel.class);

        adapter = new LocalRepoAdapter(new LocalRepoAdapter.Listener() {
            @Override
            public void onRepoClick(
                    @NonNull final LocalViewModel.Row row
            ) {
                if (selectionActive()) {
                    viewModel.toggleSelection(row.key());
                } else {
                    openDetail(row);
                }
            }

            @Override
            public void onRepoLongClick(
                    @NonNull final LocalViewModel.Row row
            ) {
                viewModel.toggleSelection(row.key());
            }
        });
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null);
        binding.refresh.setOnRefreshListener(() -> viewModel.refresh());
        binding.fabClone.setOnClickListener(button -> openDiscover());

        setupToolbar();
        setupFilterChips();
        leaveSelection = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                viewModel.clearSelection();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), leaveSelection);

        getParentFragmentManager().setFragmentResultListener(
                FolderPickerFragment.RESULT_KEY,
                getViewLifecycleOwner(),
                (key, result) -> {
                    final String path = result.getString(FolderPickerFragment.RESULT_PATH);
                    if (path != null) {
                        viewModel.addWorkspace(new File(path));
                    }
                }
        );

        getChildFragmentManager().setFragmentResultListener(NewRepoSheetFragment.RESULT_KEY, getViewLifecycleOwner(),
                (key, result) -> Snackbar.make(binding.getRoot(), R.string.new_repo_started, Snackbar.LENGTH_LONG)
                        .setAction(R.string.clone_open_activity, action ->
                                NavHostFragment.findNavController(this).navigate(R.id.activityFragment))
                        .show());

        getParentFragmentManager().setFragmentResultListener(RepoDetailFragment.RESULT_DELETED_KEY, getViewLifecycleOwner(),
                (key, result) -> Snackbar.make(binding.getRoot(), R.string.repo_deleted, Snackbar.LENGTH_LONG).show());

        viewModel.phase().observe(getViewLifecycleOwner(), phase -> renderPhase());
        viewModel.list().observe(getViewLifecycleOwner(), state -> renderList());
        viewModel.messages().observe(getViewLifecycleOwner(), this::showMessage);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        adapter = null;
        super.onDestroyView();
    }

    private boolean selectionActive() {
        final LocalViewModel.ListState state = viewModel.list().getValue();
        return state != null && state.selectedCount() > 0;
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Kopfleiste und Filter                                                                      */
    /* ------------------------------------------------------------------------------------------ */

    private void setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItem);
        binding.toolbar.setNavigationOnClickListener(view -> viewModel.clearSelection());
        final MenuItem search = binding.toolbar.getMenu().findItem(R.id.action_search);
        final SearchView searchView = (SearchView) search.getActionView();
        searchView.setQueryHint(getString(R.string.local_search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(
                    final String query
            ) {
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(
                    final String newText
            ) {
                viewModel.setQuery(newText);
                return true;
            }
        });
        search.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(
                    @NonNull final MenuItem item
            ) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(
                    @NonNull final MenuItem item
            ) {
                viewModel.setQuery("");
                return true;
            }
        });
    }

    private boolean onMenuItem(
            @NonNull final MenuItem item
    ) {
        final int id = item.getItemId();
        if (id == R.id.action_update_selected) {
            startBatch(viewModel::updateSelected);
        } else if (id == R.id.action_push_selected) {
            startBatch(viewModel::pushSelected);
        } else if (id == R.id.action_fetch_selected) {
            startBatch(viewModel::fetchSelected);
        } else if (id == R.id.action_select_all) {
            viewModel.selectAllVisible();
        } else if (id == R.id.action_update_all) {
            startBatch(viewModel::updateAll);
        } else if (id == R.id.action_new_repo) {
            NewRepoSheetFragment.show(getChildFragmentManager());
        } else {
            return false;
        }
        return true;
    }

    /** Startet eine Sammelaktion und meldet, wie viele Vorgänge daraus wurden. */
    private void startBatch(
            @NonNull final IntSupplier action
    ) {
        notificationGate.run(() -> {
            final int started = action.getAsInt();
            if (binding == null) {
                return;
            }
            if (started == 0) {
                Snackbar.make(binding.getRoot(), R.string.local_nothing_to_do, Snackbar.LENGTH_LONG).show();
                return;
            }
            Snackbar.make(binding.getRoot(), getString(R.string.clone_started, started), Snackbar.LENGTH_LONG)
                    .setAction(R.string.clone_open_activity, view ->
                            NavHostFragment.findNavController(this).navigate(R.id.activityFragment))
                    .show();
        });
    }

    private void setupFilterChips() {
        binding.filterChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (updatingChips || checkedIds.isEmpty()) {
                return;
            }
            viewModel.setFilter(filterFor(checkedIds.get(0)));
        });
    }

    private static LocalViewModel.Filter filterFor(
            final int chipId
    ) {
        if (chipId == R.id.filter_changed) {
            return LocalViewModel.Filter.CHANGED;
        }
        if (chipId == R.id.filter_ahead) {
            return LocalViewModel.Filter.AHEAD;
        }
        if (chipId == R.id.filter_behind) {
            return LocalViewModel.Filter.BEHIND;
        }
        if (chipId == R.id.filter_conflict) {
            return LocalViewModel.Filter.CONFLICT;
        }
        return LocalViewModel.Filter.ALL;
    }

    private void updateChip(
            final int chipId,
            final int label,
            final LocalViewModel.ListState state,
            final LocalViewModel.Filter filter
    ) {
        final Chip chip = binding.filterChips.findViewById(chipId);
        final Integer count = state.counts().get(filter);
        final String name = getString(label);
        chip.setText(filter != LocalViewModel.Filter.ALL && count != null && count > 0
                ? getString(R.string.local_filter_with_count, name, count)
                : name);
        chip.setChecked(state.filter() == filter);
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Zeichnen                                                                                   */
    /* ------------------------------------------------------------------------------------------ */

    private void renderList() {
        final LocalViewModel.ListState state = viewModel.list().getValue();
        if (state == null) {
            return;
        }
        adapter.submitList(state.rows());
        leaveSelection.setEnabled(state.selectedCount() > 0);

        updatingChips = true;
        updateChip(R.id.filter_all, R.string.local_filter_all, state, LocalViewModel.Filter.ALL);
        updateChip(R.id.filter_changed, R.string.local_filter_changed, state, LocalViewModel.Filter.CHANGED);
        updateChip(R.id.filter_ahead, R.string.local_filter_ahead, state, LocalViewModel.Filter.AHEAD);
        updateChip(R.id.filter_behind, R.string.local_filter_behind, state, LocalViewModel.Filter.BEHIND);
        updateChip(R.id.filter_conflict, R.string.local_filter_conflict, state, LocalViewModel.Filter.CONFLICT);
        updatingChips = false;

        final boolean selecting = state.selectedCount() > 0;
        binding.toolbar.setTitle(selecting
                ? getString(R.string.local_selected_count, state.selectedCount())
                : getString(R.string.local_title));
        if (selecting) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_close);
            binding.toolbar.setNavigationContentDescription(R.string.local_clear_selection);
        } else {
            binding.toolbar.setNavigationIcon(null);
        }
        final Menu menu = binding.toolbar.getMenu();
        menu.findItem(R.id.action_update_selected).setVisible(selecting);
        menu.findItem(R.id.action_push_selected).setVisible(selecting);
        menu.findItem(R.id.action_select_all).setVisible(selecting);
        menu.findItem(R.id.action_fetch_selected).setVisible(selecting);
        menu.findItem(R.id.action_search).setVisible(!selecting && state.total() > 0);
        menu.findItem(R.id.action_update_all).setVisible(!selecting && state.total() > 0);
        menu.findItem(R.id.action_new_repo).setVisible(!selecting);

        renderPhase();
    }

    private void renderPhase() {
        final LocalViewModel.Phase phase = viewModel.phase().getValue();
        final LocalViewModel.ListState state = viewModel.list().getValue();
        if (phase == null || state == null) {
            return;
        }
        final boolean loading = phase == LocalViewModel.Phase.LOADING;
        binding.progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        binding.refresh.setRefreshing(false);

        final boolean hasList = phase == LocalViewModel.Phase.LIST || loading;
        final boolean noMatch = phase == LocalViewModel.Phase.LIST && state.rows().isEmpty() && state.total() > 0;
        binding.filterScroll.setVisibility(hasList && state.total() > 0 ? View.VISIBLE : View.GONE);
        binding.refresh.setVisibility(hasList && !noMatch ? View.VISIBLE : View.GONE);
        binding.empty.setVisibility(hasList && !noMatch ? View.GONE : View.VISIBLE);
        if (phase == LocalViewModel.Phase.LIST && state.selectedCount() == 0) {
            binding.fabClone.show();
        } else {
            binding.fabClone.hide();
        }

        if (noMatch) {
            showEmpty(R.drawable.ic_search, getString(R.string.local_no_match_title),
                    getString(R.string.local_no_match_body),
                    getString(R.string.local_no_match_reset), this::resetFilters, null, null);
            return;
        }
        switch (phase) {
            case NEEDS_PERMISSION:
                showEmpty(R.drawable.ic_lock, getString(R.string.local_permission_title),
                        getString(R.string.local_permission_body),
                        getString(R.string.local_permission_action), this::openPermissionSettings, null, null);
                break;
            case NEEDS_WORKSPACE:
                showEmpty(R.drawable.ic_folder, getString(R.string.local_workspace_title),
                        getString(R.string.local_workspace_body),
                        getString(R.string.local_workspace_create_default), () -> viewModel.createDefaultWorkspace(),
                        getString(R.string.local_workspace_choose_other), this::openFolderPicker);
                break;
            case EMPTY:
                final File root = viewModel.defaultRoot().getValue();
                showEmpty(R.drawable.ic_folder_open, getString(R.string.local_empty_title),
                        getString(R.string.local_empty_body, root == null ? "" : root.getAbsolutePath()),
                        getString(R.string.local_empty_action), this::openDiscover,
                        getString(R.string.local_empty_rescan), () -> viewModel.refresh());
                break;
            case LOADING:
            case LIST:
            default:
                break;
        }
    }

    private void resetFilters() {
        viewModel.setFilter(LocalViewModel.Filter.ALL);
        viewModel.setQuery("");
        final MenuItem search = binding.toolbar.getMenu().findItem(R.id.action_search);
        if (search.isActionViewExpanded()) {
            search.collapseActionView();
        }
    }

    private void showEmpty(
            final int icon,
            @NonNull final String title,
            @NonNull final String body,
            @NonNull final String primaryText,
            @NonNull final Runnable primaryAction,
            @Nullable final String secondaryText,
            @Nullable final Runnable secondaryAction
    ) {
        binding.emptyIcon.setImageResource(icon);
        binding.emptyTitle.setText(title);
        binding.emptyBody.setText(body);
        binding.emptyPrimary.setText(primaryText);
        binding.emptyPrimary.setOnClickListener(button -> primaryAction.run());
        if (secondaryText == null || secondaryAction == null) {
            binding.emptySecondary.setVisibility(View.GONE);
        } else {
            binding.emptySecondary.setText(secondaryText);
            binding.emptySecondary.setOnClickListener(button -> secondaryAction.run());
            binding.emptySecondary.setVisibility(View.VISIBLE);
        }
    }

    private void openPermissionSettings() {
        try {
            startActivity(StoragePermission.settingsIntent(requireContext()));
        } catch (final ActivityNotFoundException noSettings) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    private void openFolderPicker() {
        NavHostFragment.findNavController(this).navigate(R.id.folderPickerFragment);
    }

    private void openDetail(
            @NonNull final LocalViewModel.Row row
    ) {
        final Bundle arguments = new Bundle();
        arguments.putString(RepoDetailFragment.ARG_DIRECTORY, row.repo().directory().getAbsolutePath());
        NavHostFragment.findNavController(this).navigate(R.id.repoDetailFragment, arguments);
    }

    private void openDiscover() {
        NavHostFragment.findNavController(this).navigate(R.id.discoverFragment);
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
