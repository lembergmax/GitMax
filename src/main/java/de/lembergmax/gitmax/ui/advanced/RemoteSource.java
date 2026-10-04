package de.lembergmax.gitmax.ui.advanced;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.RemoteInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Die Remote-Liste: hinzufügen, Adresse ändern, zwischen HTTPS und SSH umstellen, umbenennen, entfernen. */
final class RemoteSource implements ListSource {

    private static final String SET_URL = "set_url";
    private static final String SWITCH = "switch";
    private static final String RENAME = "rename";
    private static final String REMOVE = "remove";

    private final Context context;
    private final ServiceLocator services;
    private final File repo;

    RemoteSource(
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
        return R.string.remotes_title;
    }

    @Override
    public int emptyTitle() {
        return R.string.remotes_empty_title;
    }

    @Override
    public int emptyBody() {
        return R.string.remotes_empty_body;
    }

    @NonNull
    @Override
    public List<SimpleRow> load() throws GitFailureException {
        final List<SimpleRow> rows = new ArrayList<>();
        for (final RemoteInfo remote : services.advanced().remotes(repo)) {
            rows.add(new SimpleRow("remote:" + remote.name(), remote.name(), remote.url(), R.drawable.ic_cloud_upload, null, false, remote));
        }
        return rows;
    }

    @NonNull
    @Override
    public List<RowAction> actions(
            @NonNull final SimpleRow row
    ) {
        final RemoteInfo remote = (RemoteInfo) row.payload();
        final String url = remote == null ? "" : remote.url();
        final List<RowAction> actions = new ArrayList<>();
        actions.add(new RowAction(SET_URL, R.string.remotes_set_url, new Prompt(R.string.remotes_set_url, R.string.identity_save,
                List.of(new Field(R.string.remotes_url_hint, url, false, true)), List.of()), null));
        final Optional<RemoteUrl> parsed = RemoteUrl.parse(url);
        if (parsed.isPresent()) {
            actions.add(new RowAction(SWITCH, parsed.get().scheme() == RemoteUrl.Scheme.SSH
                    ? R.string.remotes_switch_https : R.string.remotes_switch_ssh, null, null));
        }
        actions.add(new RowAction(RENAME, R.string.remotes_rename, new Prompt(R.string.remotes_rename, R.string.remotes_rename,
                List.of(new Field(R.string.remotes_name_hint, row.title(), false, true)), List.of()), null));
        actions.add(new RowAction(REMOVE, R.string.remotes_remove, null, new Confirm(R.string.remotes_remove,
                context.getString(R.string.remotes_remove_body, row.title()), R.string.remotes_remove)));
        return actions;
    }

    @Nullable
    @Override
    public Prompt createPrompt() {
        return new Prompt(R.string.remotes_new_title, R.string.files_create,
                List.of(new Field(R.string.remotes_name_hint, "", false, true), new Field(R.string.remotes_url_hint, "", false, true)),
                List.of());
    }

    @Override
    public void create(
            @NonNull final Values values
    ) throws GitFailureException {
        services.advanced().addRemote(repo, values.field(0).strip(), values.field(1).strip());
    }

    @Override
    public int perform(
            @NonNull final SimpleRow row,
            @NonNull final String actionId,
            @NonNull final Values values
    ) throws GitFailureException {
        switch (actionId) {
            case SET_URL:
                services.advanced().setRemoteUrl(repo, row.title(), values.field(0).strip());
                return R.string.remotes_url_changed;
            case SWITCH:
                return switchTransport(row);
            case RENAME:
                services.advanced().renameRemote(repo, row.title(), values.field(0).strip());
                return R.string.remotes_renamed;
            case REMOVE:
                services.advanced().removeRemote(repo, row.title());
                return R.string.remotes_removed;
            default:
                return 0;
        }
    }

    /** Stellt die Adresse des Remotes von HTTPS auf SSH um oder zurück; Zugangsdaten stehen nie in der Adresse. */
    private int switchTransport(
            @NonNull final SimpleRow row
    ) throws GitFailureException {
        final RemoteInfo remote = (RemoteInfo) row.payload();
        final Optional<RemoteUrl> parsed = remote == null ? Optional.empty() : RemoteUrl.parse(remote.url());
        if (parsed.isEmpty()) {
            return 0;
        }
        final RemoteUrl url = parsed.get();
        services.advanced().setRemoteUrl(repo, row.title(),
                url.scheme() == RemoteUrl.Scheme.SSH ? url.toHttpsUrl() : url.toSshUrl());
        return R.string.remotes_switched;
    }
}
