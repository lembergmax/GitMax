package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Name und E-Mail-Adresse, mit denen Commits entstehen.
 *
 * @param name  Anzeigename des Autors
 * @param email E-Mail-Adresse des Autors
 */
public record CommitIdentity(
        @NonNull String name,
        @NonNull String email
) {

    public CommitIdentity {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        name = name.trim();
        email = email.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("The commit identity name must not be empty");
        }
        if (!isPlausibleEmail(email)) {
            throw new IllegalArgumentException("Not a valid email address: '" + email + "'");
        }
    }

    /** Grobe Prüfung ({@code x@y}), die tippfehlerhafte Eingaben abfängt, ohne RFC-Pedanterie. */
    public static boolean isPlausibleEmail(
            final String email
    ) {
        if (email == null) {
            return false;
        }
        final int at = email.indexOf('@');
        return at > 0 && at == email.lastIndexOf('@') && at < email.length() - 1
                && email.indexOf(' ') < 0 && email.indexOf('.', at) > at + 1;
    }
}
