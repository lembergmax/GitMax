package de.lembergmax.gitmax.ui.advanced;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.RemoteInfo;
import de.lembergmax.gitmax.domain.model.TagInfo;
import de.lembergmax.gitmax.ops.Operations;
import de.lembergmax.gitmax.ui.common.RelativeTime;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Die Tag-Liste: anlegen, auschecken, lokal löschen, auf dem Remote pushen und löschen. */
final class TagSource implements ListSource {

    private static final String CHECKOUT = "checkout";
    private static final String PUSH = "push";
    private static final String DELETE = "delete";
    private static final String DELETE_REMOTE = "delete_remote";
    private static final CommitIdentity FALLBACK_TAGGER = new CommitIdentity("GitMax", "gitmax@localhost.invalid");

    private final Context context;
    private final ServiceLocator services;
    private final File repo;

    TagSource(
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
        return R.string.tags_title;
    }

    @Override
    public int emptyTitle() {
        return R.string.tags_empty_title;
    }

    @Override
    public int emptyBody() {
        return R.string.tags_empty_body;
    }

    @NonNull
    @Override
    public List<SimpleRow> load() throws GitFailureException {
        final List<SimpleRow> rows = new ArrayList<>();
        final long now = System.currentTimeMillis();
        for (final TagInfo tag : services.advanced().tags(repo)) {
            final List<String> parts = new ArrayList<>();
            parts.add(context.getString(tag.annotated() ? R.string.tags_annotated : R.string.tags_lightweight));
            parts.add(tag.targetId().substring(0, 7));
            if (tag.timeMillis() > 0L) {
                parts.add(RelativeTime.format(context, tag.timeMillis(), now));
            }
            if (!tag.message().isEmpty()) {
                parts.add(tag.message().split("\n", 2)[0]);
            }
            rows.add(new SimpleRow("tag:" + tag.name(), tag.name(), String.join(" · ", parts), R.drawable.ic_sell, null, false, tag));
        }
        return rows;
    }

    @NonNull
    @Override
    public List<RowAction> actions(
            @NonNull final SimpleRow row
    ) {
        return List.of(
                new RowAction(CHECKOUT, R.string.tags_checkout, null, null),
                new RowAction(PUSH, R.string.tags_push, null, null),
                new RowAction(DELETE, R.string.tags_delete, null, new Confirm(R.string.tags_delete,
                        context.getString(R.string.tags_delete_body, row.title()), R.string.tags_delete)),
                new RowAction(DELETE_REMOTE, R.string.tags_delete_remote, null, new Confirm(R.string.tags_delete_remote,
                        context.getString(R.string.tags_delete_remote_body, row.title()), R.string.tags_delete)));
    }

    @Nullable
    @Override
    public Prompt createPrompt() {
        return new Prompt(R.string.tags_new_title, R.string.files_create,
                List.of(new Field(R.string.tags_name_hint, "", false, true), new Field(R.string.tags_message_hint, "", true, false)),
                List.of());
    }

    @Override
    public void create(
            @NonNull final Values values
    ) throws GitFailureException {
        final String message = values.field(1).strip();
        services.advanced().createTag(repo, values.field(0).strip(), null, message.isEmpty() ? null : message,
                services.identities().resolve(repo).orElse(FALLBACK_TAGGER));
    }

    @Override
    public int perform(
            @NonNull final SimpleRow row,
            @NonNull final String actionId,
            @NonNull final Values values
    ) throws GitFailureException {
        final TagInfo tag = (TagInfo) row.payload();
        if (tag == null) {
            return 0;
        }
        switch (actionId) {
            case CHECKOUT:
                services.advanced().checkoutCommit(repo, tag.name());
                return R.string.tags_checked_out;
            case DELETE:
                services.advanced().deleteTag(repo, tag.name());
                return R.string.tags_deleted;
            case PUSH:
                services.operations().enqueue(Operations.pushRef(repo, remoteName(),
                        "refs/tags/" + tag.name() + ":refs/tags/" + tag.name()));
                return R.string.tags_push_started;
            case DELETE_REMOTE:
                services.operations().enqueue(Operations.pushRef(repo, remoteName(), ":refs/tags/" + tag.name()));
                return R.string.tags_delete_remote_started;
            default:
                return 0;
        }
    }

    /** Das Remote für Netzwerkaktionen: {@code origin}, sonst das erste. */
    private String remoteName() throws GitFailureException {
        final List<RemoteInfo> remotes = services.advanced().remotes(repo);
        for (final RemoteInfo remote : remotes) {
            if (remote.name().equals("origin")) {
                return remote.name();
            }
        }
        if (remotes.isEmpty()) {
            throw new GitFailureException(GitFailureKind.NO_UPSTREAM, "The repo has no remote", null);
        }
        return remotes.get(0).name();
    }
}
