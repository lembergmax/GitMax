package de.lembergmax.gitmax.ui.advanced;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.FragmentSimpleListBinding;
import de.lembergmax.gitmax.domain.model.CommitInfo;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;

import java.util.List;

/** Der Verlauf eines Repos: Commits mit Suche, Filtern und Wechsel zwischen aktuellem und allen Branches. */
public final class HistoryFragment extends Fragment {

    /** Pfad des Repos. */
    public static final String ARG_DIRECTORY = "directory";

    /** Optionaler Pfad, auf den der Verlauf eingegrenzt wird. */
    public static final String ARG_PATH = "path";

    private FragmentSimpleListBinding binding;
    private HistoryViewModel viewModel;
    private CommitAdapter adapter;

    public HistoryFragment() {
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
        viewModel = new ViewModelProvider(this).get(HistoryViewModel.class);
        final String directory = requireArguments().getString(ARG_DIRECTORY);
        if (directory == null) {
            throw new IllegalStateException("No repo path passed");
        }
        viewModel.init(directory, requireArguments().getString(ARG_PATH));

        Toolbars.setupBack(this, binding.toolbar);
        binding.toolbar.setTitle(R.string.advanced_history);
        binding.toolbar.inflateMenu(R.menu.menu_history);
        binding.toolbar.getMenu().findItem(R.id.action_create).setVisible(false);
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItem);
        setupSearch();
        binding.emptyTitle.setText(R.string.history_empty_title);
        binding.emptyBody.setText(R.string.history_empty_body);

        adapter = new CommitAdapter(this::open);
        final LinearLayoutManager layout = new LinearLayoutManager(requireContext());
        binding.list.setLayoutManager(layout);
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null);
        binding.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(
                    @NonNull final RecyclerView list,
                    final int dx,
                    final int dy
            ) {
                if (layout.findLastVisibleItemPosition() >= adapter.getItemCount() - 5) {
                    viewModel.loadMore();
                }
            }
        });

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.messages().observe(getViewLifecycleOwner(), this::showMessage);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        adapter = null;
        super.onDestroyView();
    }

    private void render(
            @NonNull final HistoryViewModel.UiState state
    ) {
        binding.progress.setVisibility(state.loading() ? View.VISIBLE : View.INVISIBLE);
        adapter.submitList(state.commits());
        final boolean empty = state.commits().isEmpty() && !state.loading();
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.toolbar.getMenu().findItem(R.id.action_all_branches).setChecked(state.allBranches());
    }

    private void setupSearch() {
        final MenuItem search = binding.toolbar.getMenu().findItem(R.id.action_search);
        final SearchView searchView = (SearchView) search.getActionView();
        searchView.setQueryHint(getString(R.string.history_search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(
                    final String query
            ) {
                viewModel.setText(query);
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(
                    final String newText
            ) {
                return false;
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
                viewModel.setText("");
                return true;
            }
        });
    }

    private boolean onMenuItem(
            @NonNull final MenuItem item
    ) {
        if (item.getItemId() == R.id.action_all_branches) {
            viewModel.setAllBranches(!item.isChecked());
            return true;
        }
        if (item.getItemId() == R.id.action_filter) {
            final HistoryViewModel.UiState state = viewModel.state().getValue();
            final ListSource.Prompt prompt = new ListSource.Prompt(R.string.history_filter, R.string.history_filter_apply,
                    List.of(new ListSource.Field(R.string.history_filter_author, state == null ? "" : state.author(), false, false),
                            new ListSource.Field(R.string.history_filter_path, state == null ? "" : state.path(), false, false)),
                    List.of());
            PromptDialog.show(requireContext(), getLayoutInflater(), prompt,
                    values -> viewModel.setFilter(values.field(0), values.field(1)));
            return true;
        }
        return false;
    }

    private void open(
            @NonNull final CommitInfo commit
    ) {
        final Bundle arguments = new Bundle();
        arguments.putString(CommitDetailFragment.ARG_DIRECTORY, viewModel.repo().getAbsolutePath());
        arguments.putString(CommitDetailFragment.ARG_COMMIT, commit.id());
        NavHostFragment.findNavController(this).navigate(R.id.commitDetailFragment, arguments);
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
