package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Eine Datei mit ungelöstem Konflikt.
 *
 * @param path       Pfad im Repo
 * @param kind       Art des Konflikts
 * @param hasMarkers die Datei enthält noch Konfliktmarken ({@code <<<<<<<})
 */
public record ConflictFile(
        @NonNull String path,
        @NonNull ConflictKind kind,
        boolean hasMarkers
) {

    public ConflictFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
    }
}
