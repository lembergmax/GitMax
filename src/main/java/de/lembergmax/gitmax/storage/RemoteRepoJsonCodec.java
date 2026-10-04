package de.lembergmax.gitmax.storage;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.RemoteRepo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cache-Format der Repo-Listen eines Kontos. Tolerantes Lesen wie beim {@link AccountJsonCodec}.
 */
public final class RemoteRepoJsonCodec {

    /**
     * Zwischengespeicherte Repo-Liste.
     *
     * @param fetchedAtMillis Zeitpunkt des Abrufs in Millisekunden seit 1970
     * @param repos           die Repos
     */
    public record Snapshot(
            long fetchedAtMillis,
            @NonNull List<RemoteRepo> repos
    ) {

        public Snapshot {
            repos = List.copyOf(Objects.requireNonNull(repos, "repos"));
        }
    }

    private RemoteRepoJsonCodec() {
    }

    @NonNull
    public static String encode(
            @NonNull final Snapshot snapshot
    ) {
        final JSONArray items = new JSONArray();
        for (final RemoteRepo repo : snapshot.repos()) {
            try {
                final JSONObject node = new JSONObject();
                node.put("accountId", repo.accountId());
                node.put("remoteId", repo.remoteId());
                node.put("name", repo.name());
                node.put("fullPath", repo.fullPath());
                node.put("description", repo.description());
                node.put("isPrivate", repo.isPrivate());
                node.put("isArchived", repo.isArchived());
                node.put("isFork", repo.isFork());
                node.put("defaultBranch", repo.defaultBranch());
                node.put("httpsUrl", repo.httpsUrl());
                node.put("sshUrl", repo.sshUrl());
                node.put("webUrl", repo.webUrl());
                node.put("lastActivityMillis", repo.lastActivityMillis());
                items.put(node);
            } catch (final JSONException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        try {
            return new JSONObject()
                    .put("fetchedAtMillis", snapshot.fetchedAtMillis())
                    .put("repos", items)
                    .toString();
        } catch (final JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Liest eine Liste; bei unlesbarem Inhalt eine leere Liste mit Zeitpunkt 0. */
    @NonNull
    public static Snapshot decode(
            final String json
    ) {
        if (json == null || json.isBlank()) {
            return new Snapshot(0L, List.of());
        }
        try {
            final JSONObject root = new JSONObject(json);
            final JSONArray items = root.optJSONArray("repos");
            final List<RemoteRepo> repos = new ArrayList<>();
            if (items != null) {
                for (int index = 0; index < items.length(); index += 1) {
                    final JSONObject node = items.optJSONObject(index);
                    if (node != null) {
                        decodeOne(node).ifPresent(repos::add);
                    }
                }
            }
            return new Snapshot(root.optLong("fetchedAtMillis"), repos);
        } catch (final JSONException malformed) {
            return new Snapshot(0L, List.of());
        }
    }

    private static Optional<RemoteRepo> decodeOne(
            final JSONObject node
    ) {
        try {
            return Optional.of(new RemoteRepo(
                    node.getString("accountId"),
                    node.getString("remoteId"),
                    node.getString("name"),
                    node.getString("fullPath"),
                    node.optString("description", ""),
                    node.optBoolean("isPrivate"),
                    node.optBoolean("isArchived"),
                    node.optBoolean("isFork"),
                    node.optString("defaultBranch", ""),
                    node.getString("httpsUrl"),
                    node.optString("sshUrl", ""),
                    node.optString("webUrl", ""),
                    node.optLong("lastActivityMillis")
            ));
        } catch (final JSONException unreadable) {
            return Optional.empty();
        }
    }
}
