package de.lembergmax.gitmax.ui.advanced;

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
import de.lembergmax.gitmax.databinding.FragmentSimpleListBinding;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;

import java.util.List;

/**
 * Eine einfache Liste mit Aktionen je Zeile und einem Anlegen-Knopf: Branches, Stash, Tags oder Remotes,
 * je nach Argument. Die Art der Liste liefert die {@link ListSource}.
 */
public final class AdvancedListFragment extends Fragment {

    /** Art der Liste, siehe {@link ListSources}. */
    public static final String ARG_KIND = "kind";

    /** Pfad des Repos. */
    public static final String ARG_DIRECTORY = "directory";

    private FragmentSimpleListBinding binding;
    private AdvancedListViewModel viewModel;
    private SimpleRowAdapter adapter;

    public AdvancedListFragment() {
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
        viewModel = new ViewModelProvider(this).get(AdvancedListViewModel.class);
        final String kind = requireArguments().getString(ARG_KIND);
        final String directory = requireArguments().getString(ARG_DIRECTORY);
        if (kind == null || directory == null) {
            throw new IllegalStateException("List not specified");
        }
        viewModel.init(kind, directory);
        final ListSource source = viewModel.source();

        Toolbars.setupBack(this, binding.toolbar);
        binding.toolbar.setTitle(source.title());
        binding.emptyTitle.setText(source.emptyTitle());
        binding.emptyBody.setText(source.emptyBody());
        final ListSource.Prompt createPrompt = source.createPrompt();
        final MenuItem create = binding.toolbar.getMenu().findItem(R.id.action_create);
        create.setVisible(createPrompt != null);
        if (createPrompt != null) {
            create.setTitle(createPrompt.title());
            binding.toolbar.setOnMenuItemClickListener(item -> {
                PromptDialog.show(requireContext(), getLayoutInflater(), createPrompt, viewModel::create);
                return true;
            });
        }

        adapter = new SimpleRowAdapter(this::showActions, true);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null);

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
            @NonNull final AdvancedListViewModel.UiState state
    ) {
        binding.progress.setVisibility(state.loading() ? View.VISIBLE : View.INVISIBLE);
        adapter.submitList(state.rows());
        final boolean empty = state.rows().isEmpty() && !state.loading();
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showActions(
            @NonNull final View anchor,
            @NonNull final SimpleRow row
    ) {
        final List<ListSource.RowAction> actions = viewModel.source().actions(row);
        if (actions.isEmpty()) {
            return;
        }
        final PopupMenu menu = new PopupMenu(requireContext(), anchor);
        for (int index = 0; index < actions.size(); index += 1) {
            menu.getMenu().add(0, index, index, actions.get(index).label());
        }
        menu.setOnMenuItemClickListener(item -> {
            run(row, actions.get(item.getItemId()));
            return true;
        });
        menu.show();
    }

    private void run(
            @NonNull final SimpleRow row,
            @NonNull final ListSource.RowAction action
    ) {
        if (action.confirm() != null) {
            final ListSource.Confirm confirm = action.confirm();
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(confirm.title())
                    .setMessage(confirm.body())
                    .setPositiveButton(confirm.confirm(), (dialog, which) -> afterConfirm(row, action))
                    .setNegativeButton(R.string.activity_cancel, null)
                    .show();
        } else {
            afterConfirm(row, action);
        }
    }

    private void afterConfirm(
            @NonNull final SimpleRow row,
            @NonNull final ListSource.RowAction action
    ) {
        if (action.prompt() != null) {
            PromptDialog.show(requireContext(), getLayoutInflater(), action.prompt(),
                    values -> viewModel.perform(row, action.id(), values));
        } else {
            viewModel.perform(row, action.id(), ListSource.Values.NONE);
        }
    }

    private void showNotice(
            @Nullable final Event<AdvancedListViewModel.Notice> event
    ) {
        final AdvancedListViewModel.Notice notice = event == null ? null : event.consume();
        if (notice == null) {
            return;
        }
        switch (notice.kind()) {
            case CONFLICT:
                Snackbar.make(binding.getRoot(), notice.text(), Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.conflicts_open, view -> openConflicts())
                        .show();
                break;
            case NOT_MERGED:
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.branches_delete)
                        .setMessage(notice.text())
                        .setPositiveButton(R.string.branches_delete_anyway, (dialog, which) -> {
                            if (notice.row() != null) {
                                viewModel.perform(notice.row(), "delete_force", ListSource.Values.NONE);
                            }
                        })
                        .setNegativeButton(R.string.activity_cancel, null)
                        .show();
                break;
            case ERROR:
            case INFO:
            default:
                Snackbar.make(binding.getRoot(), notice.text(), Snackbar.LENGTH_LONG).show();
                break;
        }
    }

    private void openConflicts() {
        final Bundle arguments = new Bundle();
        arguments.putString(ConflictsFragment.ARG_DIRECTORY, viewModel.directory());
        NavHostFragment.findNavController(this).navigate(R.id.conflictsFragment, arguments);
    }
}
