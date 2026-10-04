package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Der Diff einer Datei.
 *
 * @param path     Pfad nach der Änderung
 * @param oldPath  Pfad vor der Änderung
 * @param binary   Binärdatei: es gibt keine Zeilen
 * @param tooLarge die Datei ist zu groß für einen Zeilen-Diff
 * @param hunks    Abschnitte; leer bei Binär- und zu großen Dateien
 */
public record FileDiff(
        @NonNull String path,
        @NonNull String oldPath,
        boolean binary,
        boolean tooLarge,
        @NonNull List<DiffHunk> hunks
) {

    public FileDiff {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(oldPath, "oldPath");
        hunks = List.copyOf(Objects.requireNonNull(hunks, "hunks"));
    }

    /** {@code true}, wenn es keinen Unterschied gibt. */
    public boolean isEmpty() {
        return !binary && !tooLarge && hunks.isEmpty();
    }
}
