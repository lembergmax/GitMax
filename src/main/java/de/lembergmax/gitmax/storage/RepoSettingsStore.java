package de.lembergmax.gitmax.storage;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.UpdateRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Objects;
import java.util.Optional;

/**
 * Einstellungen je Repo, die nicht in dessen {@code .git/config} gehören: die Commit-Identität, die für
 * dieses Repo statt der des Kontos gilt, und die Update-Strategie. Schlüssel ist der Pfad des Repos.
 */
public final class RepoSettingsStore {

    private static final String IDENTITY_PREFIX = "repo.identity.";
    private static final String STRATEGY_PREFIX = "repo.strategy.";

    private final KeyValueStore store;

    public RepoSettingsStore(
            @NonNull final KeyValueStore store
    ) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Die für dieses Repo gewählte Identität; leer, wenn keine gewählt wurde oder sie unlesbar ist. */
    @NonNull
    public Optional<CommitIdentity> identity(
            @NonNull final File repo
    ) {
        final Optional<String> raw = store.get(IDENTITY_PREFIX + repo.getAbsolutePath());
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            final JSONObject json = new JSONObject(raw.get());
            return Optional.of(new CommitIdentity(json.getString("name"), json.getString("email")));
        } catch (final JSONException | IllegalArgumentException unreadable) {
            return Optional.empty();
        }
    }

    public void setIdentity(
            @NonNull final File repo,
            @NonNull final CommitIdentity identity
    ) {
        try {
            final JSONObject json = new JSONObject();
            json.put("name", identity.name());
            json.put("email", identity.email());
            store.put(IDENTITY_PREFIX + repo.getAbsolutePath(), json.toString());
        } catch (final JSONException impossible) {
            throw new IllegalStateException("Identity cannot be serialized", impossible);
        }
    }

    /** Wie dieses Repo beim Aktualisieren zusammenführt; ohne Wahl {@link UpdateRequest.Strategy#MERGE}. */
    @NonNull
    public UpdateRequest.Strategy updateStrategy(
            @NonNull final File repo
    ) {
        final Optional<String> raw = store.get(STRATEGY_PREFIX + repo.getAbsolutePath());
        if (raw.isEmpty()) {
            return UpdateRequest.Strategy.MERGE;
        }
        try {
            return UpdateRequest.Strategy.valueOf(raw.get());
        } catch (final IllegalArgumentException unknown) {
            return UpdateRequest.Strategy.MERGE;
        }
    }

    public void setUpdateStrategy(
            @NonNull final File repo,
            @NonNull final UpdateRequest.Strategy strategy
    ) {
        store.put(STRATEGY_PREFIX + repo.getAbsolutePath(), strategy.name());
    }

    /** Vergisst alles, was für dieses Repo gemerkt ist (nach dem Löschen vom Gerät); ein neuer Klon am selben Pfad beginnt frisch. */
    public void forget(
            @NonNull final File repo
    ) {
        store.remove(IDENTITY_PREFIX + repo.getAbsolutePath());
        store.remove(STRATEGY_PREFIX + repo.getAbsolutePath());
    }
}
