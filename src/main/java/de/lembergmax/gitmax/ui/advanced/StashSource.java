package de.lembergmax.gitmax.ui.advanced;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.StashInfo;
import de.lembergmax.gitmax.ui.common.RelativeTime;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Die Stash-Liste: wegstellen, anwenden, anwenden und löschen, löschen. */
final class StashSource implements ListSource {

    private static final String APPLY = "apply";
    private static final String POP = "pop";
    private static final String DROP = "drop";

    private final Context context;
    private final ServiceLocator services;
    private final File repo;

    StashSource(
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
        return R.string.stash_title;
    }

    @Override
    public int emptyTitle() {
        return R.string.stash_empty_title;
    }

    @Override
    public int emptyBody() {
        return R.string.stash_empty_body;
    }

    @NonNull
    @Override
    public List<SimpleRow> load() throws GitFailureException {
        final List<SimpleRow> rows = new ArrayList<>();
        final long now = System.currentTimeMillis();
        for (final StashInfo stash : services.advanced().stashes(repo)) {
            final String subject = stash.message().split("\n", 2)[0];
            rows.add(new SimpleRow("stash:" + stash.id(), subject,
                    "stash@{" + stash.index() + "} · " + RelativeTime.format(context, stash.timeMillis(), now),
                    R.drawable.ic_inventory_2, null, false, stash));
        }
        return rows;
    }

    @NonNull
    @Override
    public List<RowAction> actions(
            @NonNull final SimpleRow row
    ) {
        return List.of(
                new RowAction(APPLY, R.string.stash_apply, null, null),
                new RowAction(POP, R.string.stash_pop, null, null),
                new RowAction(DROP, R.string.stash_drop, null, new Confirm(R.string.stash_drop,
                        context.getString(R.string.stash_drop_body), R.string.stash_drop)));
    }

    @Nullable
    @Override
    public Prompt createPrompt() {
        return new Prompt(R.string.stash_new_title, R.string.stash_new_confirm,
                List.of(new Field(R.string.stash_message_hint, "", false, false)),
                List.of(new Toggle(R.string.stash_include_untracked, false)));
    }

    @Override
    public void create(
            @NonNull final Values values
    ) throws GitFailureException {
        services.advanced().stash(repo, values.field(0), values.toggle(0));
    }

    @Override
    public int perform(
            @NonNull final SimpleRow row,
            @NonNull final String actionId,
            @NonNull final Values values
    ) throws GitFailureException {
        final StashInfo stash = (StashInfo) row.payload();
        if (stash == null) {
            return 0;
        }
        switch (actionId) {
            case APPLY:
                services.advanced().applyStash(repo, stash.index(), false);
                return R.string.stash_applied;
            case POP:
                services.advanced().applyStash(repo, stash.index(), true);
                return R.string.stash_applied;
            case DROP:
                services.advanced().dropStash(repo, stash.index());
                return R.string.stash_dropped;
            default:
                return 0;
        }
    }
}
