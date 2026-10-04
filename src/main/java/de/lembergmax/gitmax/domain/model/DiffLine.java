package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Eine Zeile eines Diffs.
 *
 * @param type      Art der Zeile
 * @param oldNumber Zeilennummer in der alten Fassung oder 0
 * @param newNumber Zeilennummer in der neuen Fassung oder 0
 * @param text      Inhalt ohne Zeilenende
 */
public record DiffLine(
        @NonNull Type type,
        int oldNumber,
        int newNumber,
        @NonNull String text
) {

    /** Art einer Diff-Zeile. */
    public enum Type {
        CONTEXT,
        ADDED,
        REMOVED
    }

    public DiffLine {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(text, "text");
    }
}
