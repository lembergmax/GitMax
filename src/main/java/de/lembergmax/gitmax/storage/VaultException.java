package de.lembergmax.gitmax.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Der {@link SecretVault} konnte ein Geheimnis nicht schreiben oder lesen. Die Meldung enthält nie
 * ein Geheimnis.
 */
public final class VaultException extends Exception {

    /** Art des Fehlers. */
    public enum Kind {
        /** Der Keystore-Schlüssel fehlt oder ist ungültig (z. B. nach Geräte-Wiederherstellung). */
        UNAVAILABLE,
        /** Der gespeicherte Wert ist beschädigt oder gehört nicht zu diesem Schlüssel. */
        CORRUPT
    }

    private final Kind kind;

    public VaultException(
            @NonNull final Kind kind,
            @NonNull final String message,
            @Nullable final Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    @NonNull
    public Kind kind() {
        return kind;
    }
}
