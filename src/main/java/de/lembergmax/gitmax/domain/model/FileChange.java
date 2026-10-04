package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Eine Datei in einem Commit.
 *
 * @param path    Pfad nach der Änderung
 * @param oldPath Pfad vor der Änderung (bei Umbenennung verschieden von {@code path})
 * @param kind    Art der Änderung
 * @param added   hinzugekommene Zeilen, 0 bei Binärdateien
 * @param removed entfernte Zeilen, 0 bei Binärdateien
 * @param binary  Binärdatei
 */
public record FileChange(
        @NonNull String path,
        @NonNull String oldPath,
        @NonNull Kind kind,
        int added,
        int removed,
        boolean binary
) {

    /** Art der Änderung. */
    public enum Kind {
        ADDED,
        MODIFIED,
        DELETED,
        RENAMED
    }

    public FileChange {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(oldPath, "oldPath");
        Objects.requireNonNull(kind, "kind");
    }
}
