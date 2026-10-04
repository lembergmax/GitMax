package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Ein zwischengespeicherter Stand.
 *
 * @param index      Nummer im Stash, 0 ist der neueste
 * @param id         Kennung des Stash-Commits
 * @param message    Nachricht
 * @param timeMillis Zeitpunkt
 */
public record StashInfo(
        int index,
        @NonNull String id,
        @NonNull String message,
        long timeMillis
) {

    public StashInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(message, "message");
    }
}
