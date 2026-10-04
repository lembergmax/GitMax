package de.lembergmax.gitmax.domain.syntax;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Ein gefärbter Abschnitt eines Textes.
 *
 * @param start Beginn (einschließlich)
 * @param end   Ende (ausschließlich)
 * @param type  Art des Abschnitts
 */
public record SyntaxToken(
        int start,
        int end,
        @NonNull Type type
) {

    /** Art eines Abschnitts; die Oberfläche ordnet jeder Art eine Farbe zu. */
    public enum Type {
        KEYWORD,
        STRING,
        COMMENT,
        NUMBER,
        /** Name eines Tags in XML und HTML. */
        TAG,
        /** Attribut in XML, Eigenschaft in CSS, Variable in der Shell. */
        ATTRIBUTE,
        /** Schlüssel in JSON, YAML und Properties. */
        KEY,
        /** {@code true}, {@code false}, {@code null} und Verwandte. */
        LITERAL,
        /** Überschrift in Markdown, Abschnitt in INI. */
        HEADING
    }

    public SyntaxToken {
        Objects.requireNonNull(type, "type");
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid range: " + start + ".." + end);
        }
    }
}
