package de.lembergmax.gitmax.ui.advanced;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.BranchInfo;
import de.lembergmax.gitmax.ops.Operations;
import de.lembergmax.gitmax.ui.common.RelativeTime;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Die Branch-Liste: wechseln, anlegen, umbenennen, löschen, zusammenführen, aufsetzen. */
final class BranchSource implements ListSource {

    private static final String CHECKOUT = "checkout";
    private static final String MERGE = "merge";
    private static final String REBASE = "rebase";
    private static final String RENAME = "rename";
    private static final String DELETE = "delete";
    private static final String DELETE_FORCE = "delete_force";
    private static final String DELETE_REMOTE = "delete_remote";

    private final Context context;
    private final ServiceLocator services;
    private final File repo;
    private String currentBranch = "";

    BranchSource(
            @NonNull final Context context,
            @NonNull final ServiceLocator services,
            @NonNull final File repo
    ) {
        this.context = context;
        this.services = services;
        this.repo = repo;
    }

    @Override
    public int title() {
        return R.string.branches_title;
    }

    @Override
    public int emptyTitle() {
        return R.string.branches_empty_title;
    }

    @Override
    public int emptyBody() {
        return R.string.branches_empty_body;
    }

    @NonNull
    @Override
    public List<SimpleRow> load() throws GitFailureException {
        final List<SimpleRow> rows = new ArrayList<>();
        final long now = System.currentTimeMillis();
        for (final BranchInfo branch : services.advanced().branches(repo)) {
            if (branch.current()) {
                currentBranch = branch.name();
            }
            final List<String> parts = new ArrayList<>();
            if (branch.current()) {
                parts.add(context.getString(R.string.branches_current));
            }
            if (branch.ahead() > 0 || branch.behind() > 0) {
                parts.add("↑" + branch.ahead() + " ↓" + branch.behind());
            }
            if (branch.upstream() != null) {
                parts.add(context.getString(R.string.branches_follows, branch.upstream()));
            }
            parts.add(RelativeTime.format(context, branch.tipMillis(), now));
            final int icon = branch.current() ? R.drawable.ic_check_circle : branch.remote() ? R.drawable.ic_cloud_download : R.drawable.ic_fork_right;
            rows.add(new SimpleRow((branch.remote() ? "r:" : "l:") + branch.name(), branch.name(), String.join(" · ", parts), icon,
                    context.getString(branch.remote() ? R.string.branches_section_remote : R.string.branches_section_local),
                    branch.current(), branch));
        }
        return rows;
    }

    @NonNull
    @Override
    public List<RowAction> actions(
            @NonNull final SimpleRow row
    ) {
        final BranchInfo branch = (BranchInfo) row.payload();
        final List<RowAction> actions = new ArrayList<>();
        if (branch == null) {
            return actions;
        }
        if (!branch.current()) {
            actions.add(new RowAction(CHECKOUT, R.string.branches_checkout, null, null));
            actions.add(new RowAction(MERGE, R.string.branches_merge, null, new Confirm(R.string.branches_merge,
                    context.getString(R.string.branches_merge_body, branch.name(), currentBranch), R.string.branches_merge)));
            actions.add(new RowAction(REBASE, R.string.branches_rebase, null, new Confirm(R.string.branches_rebase,
                    context.getString(R.string.branches_rebase_body, currentBranch, branch.name()), R.string.branches_rebase)));
        }
        if (!branch.remote()) {
            actions.add(new RowAction(RENAME, R.string.branches_rename, new Prompt(R.string.branches_rename, R.string.branches_rename,
                    List.of(new Field(R.string.branches_name_hint, branch.name(), false, true)), List.of()), null));
            if (!branch.current()) {
                actions.add(new RowAction(DELETE, R.string.branches_delete, null, new Confirm(R.string.branches_delete,
                        context.getString(R.string.branches_delete_body, branch.name()), R.string.branches_delete)));
            }
        } else {
            actions.add(new RowAction(DELETE_REMOTE, R.string.branches_delete_remote, null, new Confirm(R.string.branches_delete_remote,
                    context.getString(R.string.branches_delete_remote_body, branch.name()), R.string.branches_delete)));
        }
        return actions;
    }

    @Nullable
    @Override
    public Prompt createPrompt() {
        return new Prompt(R.string.branches_new_title, R.string.files_create,
                List.of(new Field(R.string.branches_name_hint, "", false, true), new Field(R.string.branches_start_hint, "", false, false)),
                List.of(new Toggle(R.string.branches_checkout_toggle, true)));
    }

    @Override
    public void create(
            @NonNull final Values values
    ) throws GitFailureException {
        final String start = values.field(1).strip();
        services.advanced().createBranch(repo, values.field(0).strip(), start.isEmpty() ? null : start, values.toggle(0));
    }

    @Override
    public int perform(
            @NonNull final SimpleRow row,
            @NonNull final String actionId,
            @NonNull final Values values
    ) throws GitFailureException {
        final BranchInfo branch = (BranchInfo) row.payload();
        if (branch == null) {
            return 0;
        }
        switch (actionId) {
            case CHECKOUT:
                if (branch.remote()) {
                    services.advanced().checkoutRemoteBranch(repo, branch.name());
                } else {
                    services.advanced().checkoutBranch(repo, branch.name());
                }
                return R.string.branches_checked_out;
            case MERGE:
                final de.lembergmax.gitmax.git.GitAdvanced.MergeOutcome outcome = services.advanced().merge(repo, branch.name());
                return outcome == de.lembergmax.gitmax.git.GitAdvanced.MergeOutcome.ALREADY_UP_TO_DATE
                        ? R.string.branches_merge_nothing : R.string.branches_merged;
            case REBASE:
                services.advanced().rebase(repo, branch.name());
                return R.string.branches_rebased;
            case RENAME:
                services.advanced().renameBranch(repo, branch.name(), values.field(0).strip());
                return R.string.branches_renamed;
            case DELETE:
            case DELETE_FORCE:
                services.advanced().deleteBranch(repo, branch.name(), actionId.equals(DELETE_FORCE));
                return R.string.branches_deleted;
            case DELETE_REMOTE:
                final int slash = branch.name().indexOf('/');
                services.operations().enqueue(Operations.pushRef(repo, branch.name().substring(0, slash),
                        ":refs/heads/" + branch.name().substring(slash + 1)));
                return R.string.branches_delete_remote_started;
            default:
                return 0;
        }
    }
}
