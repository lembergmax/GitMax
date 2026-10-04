package de.lembergmax.gitmax.domain;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Optional;

/**
 * Prüft den Namen eines neuen Repos so streng, dass GitHub und GitLab ihn annehmen: Buchstaben, Ziffern, Punkt,
 * Unterstrich und Bindestrich, höchstens 100 Zeichen. Der Name ist zugleich der Ordnername auf dem Telefonspeicher.
 */
public final class RepoNames {

    /** Was an einem Namen nicht stimmt. */
    public enum Problem {
        EMPTY,
        TOO_LONG,
        INVALID_CHARACTERS,
        RESERVED
    }

    /** Längster erlaubter Name. */
    public static final int MAX_LENGTH = 100;

    private RepoNames() {
    }

    /** @return das Problem des Namens oder leer, wenn er brauchbar ist */
    @NonNull
    public static Optional<Problem> check(
            final String name
    ) {
        if (name == null || name.isBlank()) {
            return Optional.of(Problem.EMPTY);
        }
        if (name.length() > MAX_LENGTH) {
            return Optional.of(Problem.TOO_LONG);
        }
        for (int index = 0; index < name.length(); index += 1) {
            final char c = name.charAt(index);
            final boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-';
            if (!allowed) {
                return Optional.of(Problem.INVALID_CHARACTERS);
            }
        }
        final String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals(".") || lower.equals("..") || lower.endsWith(".git") || lower.startsWith(".")) {
            return Optional.of(Problem.RESERVED);
        }
        return Optional.empty();
    }
}
