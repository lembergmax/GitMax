package de.lembergmax.gitmax.ui.advanced;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.FragmentSimpleListBinding;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;

import java.util.List;

/** Der Bereich „Erweitert“ eines Repos: Einstiege zu Verlauf, Branches, Stash, Tags, Remotes, Konflikten und Einstellungen. */
public final class AdvancedFragment extends Fragment {

    /** Pfad des Repos. */
    public static final String ARG_DIRECTORY = "directory";

    private static final String HISTORY = "history";
    private static final String CONFLICTS = "conflicts";
    private static final String TOOLS = "tools";

    public AdvancedFragment() {
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
        final FragmentSimpleListBinding binding = FragmentSimpleListBinding.bind(view);
        final String directory = requireArguments().getString(ARG_DIRECTORY);
        if (directory == null) {
            throw new IllegalStateException("No repo path passed");
        }
        Toolbars.setupBack(this, binding.toolbar);
        binding.toolbar.setTitle(R.string.advanced_title);
        binding.toolbar.getMenu().findItem(R.id.action_create).setVisible(false);

        final SimpleRowAdapter adapter = new SimpleRowAdapter((anchor, row) -> open(directory, row.id()), false);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        adapter.submitList(List.of(
                row(HISTORY, R.string.advanced_history, R.string.advanced_history_hint, R.drawable.ic_history),
                row(ListSources.BRANCHES, R.string.branches_title, R.string.advanced_branches_hint, R.drawable.ic_fork_right),
                row(ListSources.STASH, R.string.stash_title, R.string.advanced_stash_hint, R.drawable.ic_inventory_2),
                row(ListSources.TAGS, R.string.tags_title, R.string.advanced_tags_hint, R.drawable.ic_sell),
                row(ListSources.REMOTES, R.string.remotes_title, R.string.advanced_remotes_hint, R.drawable.ic_cloud_upload),
                row(CONFLICTS, R.string.conflicts_title, R.string.advanced_conflicts_hint, R.drawable.ic_call_merge),
                row(TOOLS, R.string.tools_title, R.string.advanced_tools_hint, R.drawable.ic_settings)));
    }

    private SimpleRow row(
            final String id,
            final int title,
            final int hint,
            final int icon
    ) {
        return new SimpleRow(id, getString(title), getString(hint), icon, null, false, null);
    }

    private void open(
            @NonNull final String directory,
            @NonNull final String id
    ) {
        final Bundle arguments = new Bundle();
        if (HISTORY.equals(id)) {
            arguments.putString(HistoryFragment.ARG_DIRECTORY, directory);
            NavHostFragment.findNavController(this).navigate(R.id.historyFragment, arguments);
            return;
        }
        if (CONFLICTS.equals(id)) {
            arguments.putString(ConflictsFragment.ARG_DIRECTORY, directory);
            NavHostFragment.findNavController(this).navigate(R.id.conflictsFragment, arguments);
            return;
        }
        if (TOOLS.equals(id)) {
            arguments.putString(RepoToolsFragment.ARG_DIRECTORY, directory);
            NavHostFragment.findNavController(this).navigate(R.id.repoToolsFragment, arguments);
            return;
        }
        arguments.putString(AdvancedListFragment.ARG_DIRECTORY, directory);
        arguments.putString(AdvancedListFragment.ARG_KIND, id);
        NavHostFragment.findNavController(this).navigate(R.id.advancedListFragment, arguments);
    }
}
