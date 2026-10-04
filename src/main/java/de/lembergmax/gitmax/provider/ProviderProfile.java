package de.lembergmax.gitmax.provider;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Was der Anbieter über den Token-Inhaber verrät.
 *
 * @param login  Anmeldename
 * @param userId numerische Nutzer-ID
 * @param name   Anzeigename, bei fehlendem Namen der Login
 * @param email  vorgeschlagene Commit-Adresse: öffentliche bzw. Commit-Adresse des Nutzers, sonst die
 *               No-Reply-Adresse des Anbieters
 * @param scopes erteilte Rechte des Tokens, soweit der Anbieter sie verrät (sonst leer)
 */
public record ProviderProfile(
        @NonNull String login,
        long userId,
        @NonNull String name,
        @NonNull String email,
        @NonNull List<String> scopes
) {

    public ProviderProfile {
        Objects.requireNonNull(login, "login");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        scopes = List.copyOf(Objects.requireNonNull(scopes, "scopes"));
    }
}
