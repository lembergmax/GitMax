package de.lembergmax.gitmax.domain;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.ChangedFile;
import de.lembergmax.gitmax.domain.model.WorkingTree;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Schlägt für Dateien und Ordner nach, ob Git dort Änderungen sieht. Der Dateibaum markiert damit
 * Einträge; ein Ordner gilt als geändert, sobald darunter etwas geändert ist.
 */
public final class FileStatusIndex {

    /** Ohne Änderungen. */
    public static final FileStatusIndex EMPTY = new FileStatusIndex(Map.of());

    private final Map<String, ChangedFile.Kind> kinds;

    private FileStatusIndex(
            @NonNull final Map<String, ChangedFile.Kind> kinds
    ) {
        this.kinds = kinds;
    }

    /** Baut den Index aus den Änderungen eines Repos; bei mehreren Einträgen je Datei gewinnt der wichtigste. */
    @NonNull
    public static FileStatusIndex of(
            @NonNull final WorkingTree tree
    ) {
        Objects.requireNonNull(tree, "tree");
        final Map<String, ChangedFile.Kind> kinds = new HashMap<>();
        // Reihenfolge von unwichtig nach wichtig: spätere überschreiben frühere.
        tree.untracked().forEach(file -> kinds.put(file.path(), file.kind()));
        tree.staged().forEach(file -> kinds.put(file.path(), file.kind()));
        tree.unstaged().forEach(file -> kinds.put(file.path(), file.kind()));
        tree.conflicts().forEach(file -> kinds.put(file.path(), file.kind()));
        return new FileStatusIndex(Map.copyOf(kinds));
    }

    /** Art der Änderung an dieser Datei, leer wenn sie unverändert ist. */
    @NonNull
    public Optional<ChangedFile.Kind> kindOf(
            @NonNull final String path
    ) {
        return Optional.ofNullable(kinds.get(path));
    }

    /** {@code true}, wenn unterhalb des Ordners etwas geändert ist. */
    public boolean hasChangesIn(
            @NonNull final String directory
    ) {
        final String prefix = directory.isEmpty() ? "" : directory + "/";
        for (final String path : kinds.keySet()) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
