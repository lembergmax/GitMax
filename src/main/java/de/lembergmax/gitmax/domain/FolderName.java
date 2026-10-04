package de.lembergmax.gitmax.domain;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Prüft Ordnernamen für den Telefonspeicher. Android lässt dort {@code : * ? " < > | \} und {@code /}
 * in Namen nicht zu; {@code .} und {@code ..} sind keine Namen, und Leerraum am Rand fällt beim
 * Anlegen weg.
 */
public final class FolderName {

    private static final String FORBIDDEN = "/\\:*?\"<>|";

    private FolderName() {
    }

    /** {@code true}, wenn der Name als Ordnername auf dem Telefonspeicher taugt. */
    public static boolean isValid(
            @NonNull final String name
    ) {
        Objects.requireNonNull(name, "name");
        final String trimmed = name.strip();
        if (trimmed.isEmpty() || trimmed.equals(".") || trimmed.equals("..")) {
            return false;
        }
        for (int index = 0; index < trimmed.length(); index += 1) {
            final char character = trimmed.charAt(index);
            if (FORBIDDEN.indexOf(character) >= 0 || Character.isISOControl(character)) {
                return false;
            }
        }
        return true;
    }
}
