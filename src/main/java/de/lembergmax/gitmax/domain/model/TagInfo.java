package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Ein Tag.
 *
 * @param name       Name
 * @param targetId   Kennung des Commits, auf den er zeigt
 * @param annotated  {@code true} für annotierte Tags mit eigener Nachricht
 * @param message    Nachricht des annotierten Tags, sonst leer
 * @param timeMillis Zeitpunkt des Tags bzw. seines Commits
 */
public record TagInfo(
        @NonNull String name,
        @NonNull String targetId,
        boolean annotated,
        @NonNull String message,
        long timeMillis
) {

    public TagInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(message, "message");
    }
}
