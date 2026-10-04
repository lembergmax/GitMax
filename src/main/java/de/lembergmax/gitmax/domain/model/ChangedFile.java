package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Eine Datei mit Änderung im Arbeitsverzeichnis oder im Index.
 *
 * @param path Pfad relativ zum Repo mit Schrägstrichen
 * @param kind Art der Änderung
 */
public record ChangedFile(
        @NonNull String path,
        @NonNull Kind kind
) {

    /** Art der Änderung. */
    public enum Kind {
        ADDED,
        MODIFIED,
        DELETED,
        UNTRACKED,
        CONFLICT
    }

    public ChangedFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
    }
}
