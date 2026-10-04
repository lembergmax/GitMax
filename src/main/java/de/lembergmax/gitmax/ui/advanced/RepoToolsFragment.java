package de.lembergmax.gitmax.ui.advanced;

import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.FragmentSimpleListBinding;
import de.lembergmax.gitmax.domain.model.RepoStats;
import de.lembergmax.gitmax.domain.model.SubmoduleInfo;
import de.lembergmax.gitmax.domain.model.UpdateRequest;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.IdentityDialog;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;
import de.lembergmax.gitmax.ui.viewer.FileViewerFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Einstellungen und Wartung eines Repos: Commit-Identität, Update-Strategie, Bereinigung beim Abrufen,
 * {@code .gitignore} und persönliche Ausschlüsse, nicht verfolgte Dateien entfernen, Git-Daten verdichten
 * und Submodule.
 */
public final class RepoToolsFragment extends Fragment {

    /** Pfad des Repos. */
    public static final String ARG_DIRECTORY = "directory";

    private static final String IDENTITY = "identity";
    private static final String STRATEGY = "strategy";
    private static final String PRUNE = "prune";
    private static final String GITIGNORE = "gitignore";
    private static final String EXCLUDE = "exclude";
    private static final String CLEAN = "clean";
    private static final String GC = "gc";
    private static final String SUBMODULES_UPDATE = "submodules_update";
    private static final int MAX_LISTED_PATHS = 12;

    private FragmentSimpleListBinding binding;
    private RepoToolsViewModel viewModel;
    private SimpleRowAdapter adapter;

    public RepoToolsFragment() {
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
        viewModel = new ViewModelProvider(this).get(RepoToolsViewModel.class);
        final String directory = requireArguments().getString(ARG_DIRECTORY);
        if (directory == null) {
            throw new IllegalStateException("No repo path passed");
        }
        viewModel.init(directory);

        Toolbars.setupBack(this, binding.toolbar);
        binding.toolbar.setTitle(R.string.tools_title);
        binding.toolbar.getMenu().findItem(R.id.action_create).setVisible(false);
        binding.empty.setVisibility(View.GONE);

        adapter = new SimpleRowAdapter(this::onRowClick, false);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null);

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.notices().observe(getViewLifecycleOwner(), this::showNotice);
        viewModel.cleanCandidates().observe(getViewLifecycleOwner(), this::showCleanDialog);
        viewModel.excludeText().observe(getViewLifecycleOwner(), this::showExcludeDialog);
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

    /* ------------------------------------------------------------------------------------------ */
    /* Zeichnen                                                                                   */
    /* ------------------------------------------------------------------------------------------ */

    private void render(
            @NonNull final RepoToolsViewModel.UiState state
    ) {
        binding.progress.setVisibility(state.loading() || state.busy() ? View.VISIBLE : View.INVISIBLE);
        if (state.data() != null) {
            adapter.submitList(rowsOf(state.data()));
        }
    }

    private List<SimpleRow> rowsOf(
            @NonNull final RepoToolsViewModel.Data data
    ) {
        final List<SimpleRow> rows = new ArrayList<>();
        final String commits = getString(R.string.tools_section_commits);
        final String update = getString(R.string.tools_section_update);
        final String files = getString(R.string.tools_section_files);
        final String maintenance = getString(R.string.tools_section_maintenance);
        final String submodules = getString(R.string.tools_section_submodules);

        rows.add(row(IDENTITY, R.string.tools_identity, identityText(data), R.drawable.ic_person, commits));
        rows.add(row(STRATEGY, R.string.tools_strategy,
                getString(strategyLabel(data.strategy())) + "\n" + getString(R.string.tools_strategy_hint),
                R.drawable.ic_merge_type, update));
        rows.add(row(PRUNE, R.string.tools_prune, getString(data.prune() ? R.string.tools_prune_on : R.string.tools_prune_off),
                R.drawable.ic_cloud_download, update));
        rows.add(row(GITIGNORE, R.string.tools_gitignore, getString(R.string.tools_gitignore_hint),
                R.drawable.ic_visibility_off, files));
        rows.add(row(EXCLUDE, R.string.tools_exclude, getString(R.string.tools_exclude_hint),
                R.drawable.ic_description, files));
        rows.add(row(CLEAN, R.string.tools_clean, getString(R.string.tools_clean_hint), R.drawable.ic_delete_sweep, maintenance));
        rows.add(row(GC, R.string.tools_gc, getString(R.string.tools_gc_hint) + "\n" + statsText(data.stats()),
                R.drawable.ic_inventory_2, maintenance));

        // Ohne Submodule gibt es nichts zu zeigen: kein leerer Abschnitt.
        if (!data.submodules().isEmpty()) {
            for (final SubmoduleInfo module : data.submodules()) {
                rows.add(new SimpleRow("sub:" + module.path(), module.path(),
                        module.url() + "\n" + getString(submoduleState(module)), R.drawable.ic_account_tree, submodules, false, module));
            }
            rows.add(row(SUBMODULES_UPDATE, R.string.tools_submodules_update, "", R.drawable.ic_sync, submodules));
        }
        return rows;
    }

    private SimpleRow row(
            @NonNull final String id,
            @StringRes final int title,
            @NonNull final String subtitle,
            final int icon,
            @NonNull final String section
    ) {
        return new SimpleRow(id, getString(title), subtitle, icon, section, false, null);
    }

    private String identityText(
            @NonNull final RepoToolsViewModel.Data data
    ) {
        if (data.identity() == null) {
            return getString(R.string.tools_identity_none);
        }
        return getString(data.identityChosen() ? R.string.tools_identity_value : R.string.tools_identity_account,
                data.identity().name(), data.identity().email());
    }

    private String statsText(
            @NonNull final RepoStats stats
    ) {
        return getString(R.string.tools_gc_stats, stats.looseObjects(), stats.packs(),
                Formatter.formatShortFileSize(requireContext(), stats.totalBytes()));
    }

    @StringRes
    private static int strategyLabel(
            @NonNull final UpdateRequest.Strategy strategy
    ) {
        switch (strategy) {
            case REBASE:
                return R.string.tools_strategy_rebase;
            case FAST_FORWARD_ONLY:
                return R.string.tools_strategy_ff;
            case MERGE:
            default:
                return R.string.tools_strategy_merge;
        }
    }

    @StringRes
    private static int submoduleState(
            @NonNull final SubmoduleInfo module
    ) {
        if (!module.initialized()) {
            return R.string.tools_submodule_missing;
        }
        return module.upToDate() ? R.string.tools_submodule_ready : R.string.tools_submodule_outdated;
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Bedienung                                                                                  */
    /* ------------------------------------------------------------------------------------------ */

    private void onRowClick(
            @NonNull final View anchor,
            @NonNull final SimpleRow row
    ) {
        final RepoToolsViewModel.UiState state = viewModel.state().getValue();
        if (state == null || state.data() == null || state.busy()) {
            return;
        }
        switch (row.id()) {
            case IDENTITY:
                IdentityDialog.show(requireContext(), getLayoutInflater(), state.data().identity(), viewModel::chooseIdentity);
                break;
            case STRATEGY:
                chooseStrategy(state.data().strategy());
                break;
            case PRUNE:
                PromptDialog.show(requireContext(), getLayoutInflater(), new ListSource.Prompt(R.string.tools_prune, R.string.identity_save,
                        List.of(), List.of(new ListSource.Toggle(R.string.tools_prune_toggle, state.data().prune()))),
                        values -> viewModel.setPrune(values.toggle(0)));
                break;
            case GITIGNORE:
                viewModel.ensureGitignore(this::openGitignore);
                break;
            case EXCLUDE:
                viewModel.loadExclude();
                break;
            case CLEAN:
                viewModel.prepareClean();
                break;
            case GC:
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.tools_gc)
                        .setMessage(getString(R.string.tools_gc_hint) + "\n\n" + statsText(state.data().stats()))
                        .setPositiveButton(R.string.tools_gc_confirm, (dialog, which) -> viewModel.collectGarbage())
                        .setNegativeButton(R.string.activity_cancel, null)
                        .show();
                break;
            case SUBMODULES_UPDATE:
                viewModel.updateSubmodules();
                break;
            default:
                break;
        }
    }

    private void chooseStrategy(
            @NonNull final UpdateRequest.Strategy current
    ) {
        final UpdateRequest.Strategy[] options = UpdateRequest.Strategy.values();
        final String[] labels = new String[options.length];
        int checked = 0;
        for (int index = 0; index < options.length; index += 1) {
            labels[index] = getString(strategyLabel(options[index]));
            if (options[index] == current) {
                checked = index;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tools_strategy)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    viewModel.chooseStrategy(options[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.activity_cancel, null)
                .show();
    }

    private void openGitignore() {
        final Bundle arguments = new Bundle();
        arguments.putString(FileViewerFragment.ARG_DIRECTORY, viewModel.repo().getAbsolutePath());
        arguments.putString(FileViewerFragment.ARG_PATH, ".gitignore");
        arguments.putBoolean(FileViewerFragment.ARG_EDIT, true);
        NavHostFragment.findNavController(this).navigate(R.id.fileViewerFragment, arguments);
    }

    private void showExcludeDialog(
            @Nullable final Event<String> event
    ) {
        final String text = event == null ? null : event.consume();
        if (text == null) {
            return;
        }
        PromptDialog.show(requireContext(), getLayoutInflater(), new ListSource.Prompt(R.string.tools_exclude, R.string.identity_save,
                List.of(new ListSource.Field(R.string.tools_exclude_field, text, true, false)), List.of()),
                values -> viewModel.saveExclude(values.field(0)));
    }

    private void showCleanDialog(
            @Nullable final Event<List<String>> event
    ) {
        final List<String> paths = event == null ? null : event.consume();
        if (paths == null) {
            return;
        }
        final StringBuilder listed = new StringBuilder();
        for (int index = 0; index < Math.min(paths.size(), MAX_LISTED_PATHS); index += 1) {
            listed.append(paths.get(index)).append((char) 10);
        }
        if (paths.size() > MAX_LISTED_PATHS) {
            listed.append(getString(R.string.tools_clean_more, paths.size() - MAX_LISTED_PATHS));
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tools_clean_title)
                .setMessage(getString(R.string.tools_clean_body, listed.toString().stripTrailing()))
                .setPositiveButton(R.string.tools_clean_confirm, (dialog, which) -> viewModel.clean(paths))
                .setNegativeButton(R.string.activity_cancel, null)
                .show();
    }

    private void showNotice(
            @Nullable final Event<RepoToolsViewModel.Notice> event
    ) {
        final RepoToolsViewModel.Notice notice = event == null ? null : event.consume();
        if (notice != null) {
            Snackbar.make(binding.getRoot(), notice.text(), notice.error() ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT).show();
        }
    }
}
