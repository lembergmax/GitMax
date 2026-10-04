package de.lembergmax.gitmax.ui.discover;

import android.os.Bundle;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import de.lembergmax.gitmax.BuildConfig;
import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.DialogCloneUrlBinding;
import de.lembergmax.gitmax.databinding.FragmentDiscoverBinding;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.RelativeTime;

import java.util.List;
import java.util.Optional;

/**
 * Entdecken: die Repos aller verbundenen Konten mit Suche, Filtern und Mehrfachauswahl zum Klonen.
 * Ein Tipp auf eine Zeile klont dieses eine Repo; langes Drücken beginnt die Auswahl mehrerer.
 */
public final class DiscoverFragment extends Fragment {

    private FragmentDiscoverBinding binding;
    private DiscoverViewModel viewModel;
    private RemoteRepoAdapter adapter;
    /** Adresse eines Repos, das von einer anderen App geteilt wurde: das Klon-Sheet öffnet sich damit. */
    public static final String ARG_CLONE_URL = "clone_url";

    private OnBackPressedCallback leaveSelection;
    private List<DiscoverViewModel.AccountChip> shownChips = List.of();
    private boolean updatingChips;

    public DiscoverFragment() {
        super(R.layout.fragment_discover);
    }

    @Override
    public void onCreate(
            @Nullable final Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
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
        binding = FragmentDiscoverBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(DiscoverViewModel.class);

        adapter = new RemoteRepoAdapter(new RemoteRepoAdapter.Listener() {
            @Override
            public void onClick(
                    @NonNull final DiscoverViewModel.Row row
            ) {
                if (selectionActive()) {
                    viewModel.toggleSelection(row.key());
                } else {
                    CloneSheetFragment.show(getChildFragmentManager(), List.of(row.repo().httpsUrl()));
                }
            }

            @Override
            public void onLongClick(
                    @NonNull final DiscoverViewModel.Row row
            ) {
                viewModel.toggleSelection(row.key());
            }
        });
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null);
        binding.refresh.setOnRefreshListener(() -> viewModel.refresh());
        binding.bannerRetry.setOnClickListener(button -> viewModel.refresh());

        setupToolbar();
        setupFilterChips();
        leaveSelection = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                viewModel.clearSelection();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), leaveSelection);

        getChildFragmentManager().setFragmentResultListener(CloneSheetFragment.RESULT_KEY, getViewLifecycleOwner(),
                (key, result) -> onCloneStarted(result.getInt(CloneSheetFragment.RESULT_STARTED)));

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        openSharedUrl(view);
    }

    /** Öffnet das Klon-Sheet für eine geteilte Adresse, genau einmal. */
    private void openSharedUrl(
            @NonNull final View view
    ) {
        final Bundle arguments = getArguments();
        final String shared = arguments == null ? null : arguments.getString(ARG_CLONE_URL);
        if (shared == null) {
            return;
        }
        arguments.remove(ARG_CLONE_URL);
        view.post(() -> {
            if (isAdded() && getView() != null) {
                CloneSheetFragment.show(getChildFragmentManager(), List.of(shared));
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.start();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        adapter = null;
        super.onDestroyView();
    }

    private boolean selectionActive() {
        final DiscoverViewModel.UiState state = viewModel.state().getValue();
        return state != null && state.selectedCount() > 0;
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Kopfleiste                                                                                 */
    /* ------------------------------------------------------------------------------------------ */

    private void setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItem);
        binding.toolbar.setNavigationOnClickListener(view -> viewModel.clearSelection());

        final MenuItem search = binding.toolbar.getMenu().findItem(R.id.action_search);
        final SearchView searchView = (SearchView) search.getActionView();
        searchView.setQueryHint(getString(R.string.discover_search_hint));
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
        if (id == R.id.action_clone_selected) {
            CloneSheetFragment.show(getChildFragmentManager(), viewModel.selectedUrls());
        } else if (id == R.id.action_select_all) {
            viewModel.selectAllVisible();
        } else if (id == R.id.sort_recent) {
            viewModel.setSort(DiscoverViewModel.Sort.RECENT);
        } else if (id == R.id.sort_name) {
            viewModel.setSort(DiscoverViewModel.Sort.NAME);
        } else if (id == R.id.action_clone_url) {
            askForUrl();
        } else {
            return false;
        }
        return true;
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Filter                                                                                     */
    /* ------------------------------------------------------------------------------------------ */

    private void setupFilterChips() {
        binding.filterChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (updatingChips || checkedIds.isEmpty()) {
                return;
            }
            viewModel.setFilter(filterFor(checkedIds.get(0)));
        });
        binding.accountChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (updatingChips || checkedIds.isEmpty()) {
                return;
            }
            final Chip chip = group.findViewById(checkedIds.get(0));
            viewModel.selectAccount(chip == null ? null : (String) chip.getTag());
        });
    }

    private static DiscoverViewModel.Filter filterFor(
            final int chipId
    ) {
        if (chipId == R.id.filter_not_cloned) {
            return DiscoverViewModel.Filter.NOT_CLONED;
        }
        if (chipId == R.id.filter_private) {
            return DiscoverViewModel.Filter.PRIVATE;
        }
        if (chipId == R.id.filter_public) {
            return DiscoverViewModel.Filter.PUBLIC;
        }
        if (chipId == R.id.filter_forks) {
            return DiscoverViewModel.Filter.FORKS;
        }
        if (chipId == R.id.filter_archived) {
            return DiscoverViewModel.Filter.ARCHIVED;
        }
        return DiscoverViewModel.Filter.ALL;
    }

    private static int chipIdFor(
            final DiscoverViewModel.Filter filter
    ) {
        switch (filter) {
            case NOT_CLONED:
                return R.id.filter_not_cloned;
            case PRIVATE:
                return R.id.filter_private;
            case PUBLIC:
                return R.id.filter_public;
            case FORKS:
                return R.id.filter_forks;
            case ARCHIVED:
                return R.id.filter_archived;
            case ALL:
            default:
                return R.id.filter_all;
        }
    }

    private void renderAccountChips(
            @NonNull final DiscoverViewModel.UiState state
    ) {
        final boolean show = !state.accountChips().isEmpty();
        binding.accountScroll.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            shownChips = List.of();
            return;
        }
        updatingChips = true;
        if (!state.accountChips().equals(shownChips)) {
            shownChips = state.accountChips();
            binding.accountChips.removeAllViews();
            binding.accountChips.addView(newAccountChip(getString(R.string.discover_all_accounts), null));
            for (final DiscoverViewModel.AccountChip account : shownChips) {
                binding.accountChips.addView(newAccountChip(account.label(), account.id()));
            }
        }
        for (int index = 0; index < binding.accountChips.getChildCount(); index += 1) {
            final Chip chip = (Chip) binding.accountChips.getChildAt(index);
            final Object tag = chip.getTag();
            chip.setChecked(tag == null ? state.selectedAccount() == null : tag.equals(state.selectedAccount()));
        }
        updatingChips = false;
    }

    private Chip newAccountChip(
            @NonNull final String label,
            @Nullable final String accountId
    ) {
        final Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(accountId);
        chip.setCheckable(true);
        chip.setChipIconVisible(false);
        chip.setCheckedIconVisible(true);
        return chip;
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Zeichnen                                                                                   */
    /* ------------------------------------------------------------------------------------------ */

    private void render(
            @NonNull final DiscoverViewModel.UiState state
    ) {
        binding.progress.setVisibility(state.loading() ? View.VISIBLE : View.INVISIBLE);
        if (!state.loading()) {
            binding.refresh.setRefreshing(false);
        }
        renderAccountChips(state);
        updatingChips = true;
        binding.filterChips.check(chipIdFor(state.filter()));
        updatingChips = false;
        renderToolbar(state);
        adapter.submitList(state.rows());
        leaveSelection.setEnabled(state.selectedCount() > 0);

        final boolean hasRows = !state.rows().isEmpty();
        binding.refresh.setVisibility(hasRows ? View.VISIBLE : View.GONE);
        renderBanner(state);
        renderEmpty(state, hasRows);
    }

    private void renderToolbar(
            @NonNull final DiscoverViewModel.UiState state
    ) {
        final boolean selecting = state.selectedCount() > 0;
        binding.toolbar.setTitle(selecting
                ? getString(R.string.discover_selected_count, state.selectedCount())
                : getString(R.string.discover_title));
        if (selecting) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_close);
            binding.toolbar.setNavigationContentDescription(R.string.discover_clear_selection);
        } else {
            binding.toolbar.setNavigationIcon(null);
        }
        final android.view.Menu menu = binding.toolbar.getMenu();
        menu.findItem(R.id.action_clone_selected).setVisible(selecting);
        menu.findItem(R.id.action_select_all).setVisible(selecting);
        menu.findItem(R.id.action_search).setVisible(!selecting);
        menu.findItem(R.id.action_sort).setVisible(!selecting);
        menu.findItem(R.id.action_clone_url).setVisible(!selecting);
        menu.findItem(state.sort() == DiscoverViewModel.Sort.NAME ? R.id.sort_name : R.id.sort_recent).setChecked(true);
    }

    private void renderBanner(
            @NonNull final DiscoverViewModel.UiState state
    ) {
        final boolean show = state.error() != null && state.total() > 0;
        binding.banner.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            final String time = state.fetchedAt() > 0L
                    ? RelativeTime.format(requireContext(), state.fetchedAt(), System.currentTimeMillis())
                    : "";
            binding.bannerText.setText(state.fetchedAt() > 0L
                    ? getString(R.string.discover_banner_stale, state.error(), time)
                    : state.error());
        }
    }

    private void renderEmpty(
            @NonNull final DiscoverViewModel.UiState state,
            final boolean hasRows
    ) {
        if (hasRows || (state.loading() && state.total() == 0 && !state.noAccounts())) {
            binding.empty.setVisibility(View.GONE);
            return;
        }
        binding.empty.setVisibility(View.VISIBLE);
        if (state.noAccounts()) {
            showEmpty(R.drawable.ic_link_off, R.string.discover_no_accounts_title, getString(R.string.discover_no_accounts_body),
                    R.string.discover_no_accounts_action,
                    () -> NavHostFragment.findNavController(this).navigate(R.id.connectAccountFragment));
        } else if (state.total() == 0 && state.error() != null) {
            showEmpty(R.drawable.ic_cloud_off, R.string.discover_error_title, state.error(),
                    R.string.discover_error_retry, () -> viewModel.refresh());
        } else if (state.total() == 0) {
            showEmpty(R.drawable.ic_inventory_2, R.string.discover_empty_title, getString(R.string.discover_empty_none_body),
                    R.string.discover_error_retry, () -> viewModel.refresh());
        } else {
            showEmpty(R.drawable.ic_search, R.string.discover_empty_title, getString(R.string.discover_empty_filtered_body),
                    R.string.discover_empty_reset, this::resetFilters);
        }
    }

    private void showEmpty(
            final int icon,
            final int title,
            @NonNull final String body,
            final int actionText,
            @NonNull final Runnable action
    ) {
        binding.emptyIcon.setImageResource(icon);
        binding.emptyTitle.setText(title);
        binding.emptyBody.setText(body);
        binding.emptyAction.setText(actionText);
        binding.emptyAction.setOnClickListener(button -> action.run());
    }

    private void resetFilters() {
        viewModel.setFilter(DiscoverViewModel.Filter.ALL);
        viewModel.selectAccount(null);
        viewModel.setQuery("");
        final MenuItem search = binding.toolbar.getMenu().findItem(R.id.action_search);
        if (search.isActionViewExpanded()) {
            search.collapseActionView();
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Aktionen                                                                                   */
    /* ------------------------------------------------------------------------------------------ */

    private void onCloneStarted(
            final int started
    ) {
        viewModel.clearSelection();
        if (started == 0) {
            Snackbar.make(binding.getRoot(), R.string.clone_nothing_to_do, Snackbar.LENGTH_LONG).show();
            return;
        }
        Snackbar.make(binding.getRoot(), getString(R.string.clone_started, started), Snackbar.LENGTH_LONG)
                .setAction(R.string.clone_open_activity, view ->
                        NavHostFragment.findNavController(this).navigate(R.id.activityFragment))
                .show();
    }

    private void askForUrl() {
        final DialogCloneUrlBinding dialogBinding = DialogCloneUrlBinding.inflate(getLayoutInflater());
        final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clone_url_title)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.clone_url_continue, null)
                .setNegativeButton(R.string.activity_cancel, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    final String error = validate(String.valueOf(dialogBinding.urlInput.getText()));
                    if (error != null) {
                        dialogBinding.urlLayout.setError(error);
                        return;
                    }
                    dialog.dismiss();
                    CloneSheetFragment.show(getChildFragmentManager(),
                            List.of(String.valueOf(dialogBinding.urlInput.getText()).trim()));
                }));
        dialog.show();
    }

    /** Meldung, wenn die Eingabe keine klonbare Adresse ist, sonst {@code null}. */
    @Nullable
    private String validate(
            @NonNull final String input
    ) {
        final Optional<RemoteUrl> parsed = RemoteUrl.parse(input);
        if (parsed.isEmpty()) {
            return getString(R.string.clone_url_invalid);
        }
        final RemoteUrl.Scheme scheme = parsed.get().scheme();
        final boolean allowed = scheme == RemoteUrl.Scheme.HTTPS || (BuildConfig.TEST_BUILD && scheme == RemoteUrl.Scheme.HTTP);
        return allowed ? null : getString(R.string.clone_url_insecure);
    }
}
