package de.lembergmax.gitmax.repository;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Beim Erneuern eines Tokens gehörte der neue Token einem anderen Nutzer als das Konto. Der alte
 * Token bleibt dann unverändert gespeichert.
 */
public final class AccountMismatchException extends Exception {

    private final String foundLogin;

    public AccountMismatchException(
            @NonNull final String message,
            @NonNull final String foundLogin
    ) {
        super(Objects.requireNonNull(message, "message"));
        this.foundLogin = Objects.requireNonNull(foundLogin, "foundLogin");
    }

    /** Login des Nutzers, dem der abgelehnte Token gehört. */
    @NonNull
    public String foundLogin() {
        return foundLogin;
    }
}
