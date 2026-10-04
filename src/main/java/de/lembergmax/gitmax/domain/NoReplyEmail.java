package de.lembergmax.gitmax.domain;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Ausweich-E-Mail-Adressen, wenn die API keine E-Mail liefert oder der Nutzer seine private Adresse
 * nicht in Commits haben will. Beide Anbieter nehmen diese Adressen einem Konto zu.
 */
public final class NoReplyEmail {

    private NoReplyEmail() {
    }

    /** {@code <id>+<login>@users.noreply.github.com} */
    @NonNull
    public static String github(
            final long userId,
            @NonNull final String login
    ) {
        Objects.requireNonNull(login, "login");
        return userId + "+" + login + "@users.noreply.github.com";
    }

    /**
     * {@code <id>-<benutzername>@users.noreply.gitlab.com}; bei selbst gehosteten Instanzen steht der
     * eigene Host hinter {@code users.noreply.}.
     */
    @NonNull
    public static String gitlab(
            final long userId,
            @NonNull final String username,
            @NonNull final String host
    ) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(host, "host");
        final int colon = host.indexOf(':');
        final String hostname = colon < 0 ? host : host.substring(0, colon);
        return userId + "-" + username + "@users.noreply." + hostname;
    }
}
