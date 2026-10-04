package de.lembergmax.gitmax.provider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Fehler beim Sprechen mit der API eines Anbieters. Die Oberfläche bildet {@link #kind()} auf einen
 * verständlichen Text ab; die Meldung der Exception ist technisch und enthält nie einen Token.
 */
public final class ProviderException extends Exception {

    /** Art des Fehlers, grob genug für eine klare Handlungsanweisung. */
    public enum Kind {
        /** Token abgelehnt (abgelaufen, widerrufen, falsch): neu verknüpfen. */
        UNAUTHORIZED,
        /** Token gültig, aber ohne das nötige Recht. */
        FORBIDDEN,
        /** Anfragelimit erreicht: später erneut versuchen, siehe {@link #retryAfterSeconds()}. */
        RATE_LIMITED,
        /** Adresse existiert nicht (falscher Host oder keine solche API). */
        NOT_FOUND,
        /** Keine Verbindung, Zeitüberschreitung, TLS-Fehler. */
        NETWORK,
        /** Fehler auf Seiten des Anbieters (5xx). */
        SERVER,
        /** Antwort nicht lesbar oder unerwartet. */
        MALFORMED,
        /** Unter diesem Namen gibt es beim Anbieter schon ein Repo. */
        NAME_TAKEN,
        /** Der Anbieter lehnt die Eingabe ab (z. B. ungültiger Name). */
        INVALID
    }

    private final Kind kind;
    private final long retryAfterSeconds;

    public ProviderException(
            @NonNull final Kind kind,
            @NonNull final String message,
            @Nullable final Throwable cause
    ) {
        this(kind, message, 0L, cause);
    }

    public ProviderException(
            @NonNull final Kind kind,
            @NonNull final String message,
            final long retryAfterSeconds,
            @Nullable final Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.retryAfterSeconds = Math.max(0L, retryAfterSeconds);
    }

    @NonNull
    public Kind kind() {
        return kind;
    }

    /** Wartezeit in Sekunden bei {@link Kind#RATE_LIMITED}, sonst 0 (unbekannt). */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
