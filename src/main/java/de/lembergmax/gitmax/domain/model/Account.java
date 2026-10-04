package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Ein verknüpftes Konto. Der Token selbst gehört nicht hierher: er liegt im {@code SecretVault}
 * unter der {@link #id()} des Kontos.
 *
 * @param id       stabile, zufällige Kennung (UUID), Schlüssel für Token und Zuordnungen
 * @param endpoint Anbieter, Host und API-Basis-URL
 * @param login    Anmeldename beim Anbieter
 * @param userId   numerische Nutzer-ID beim Anbieter
 * @param name     Anzeigename des Nutzers (bei fehlendem Namen der Login)
 * @param identity Standard-Identität für Commits mit diesem Konto
 * @param scopes   dem Token erteilte Rechte, soweit der Anbieter sie verrät (sonst leer)
 * @param status   Zustand der Verknüpfung
 */
public record Account(
        @NonNull String id,
        @NonNull AccountEndpoint endpoint,
        @NonNull String login,
        long userId,
        @NonNull String name,
        @NonNull CommitIdentity identity,
        @NonNull List<String> scopes,
        @NonNull Status status
) {

    /** Zustand einer Verknüpfung. */
    public enum Status {
        /** Token wurde zuletzt akzeptiert. */
        ACTIVE,
        /** Der Anbieter hat den Token abgelehnt (abgelaufen, widerrufen): neu verknüpfen. */
        TOKEN_REJECTED,
        /** Der Token ist im Keystore nicht mehr lesbar (z. B. nach Geräte-Wiederherstellung). */
        SECRET_LOST
    }

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(login, "login");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(status, "status");
        scopes = List.copyOf(Objects.requireNonNull(scopes, "scopes"));
    }

    @NonNull
    public Account withStatus(
            @NonNull final Status newStatus
    ) {
        return new Account(id, endpoint, login, userId, name, identity, scopes, newStatus);
    }

    @NonNull
    public Account withIdentity(
            @NonNull final CommitIdentity newIdentity
    ) {
        return new Account(id, endpoint, login, userId, name, newIdentity, scopes, status);
    }

    /** Kurzbeschreibung für Listen: {@code GitHub · max}. */
    @NonNull
    public String label() {
        return endpoint.provider().displayName() + " · " + login;
    }
}
