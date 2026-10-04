package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Ein SSH-Schlüssel der App, ohne den privaten Teil. Der private Schlüssel liegt nur im {@code SecretVault}.
 *
 * @param id            interne Kennung
 * @param label         Name, den der Nutzer vergeben hat
 * @param type          Art des Schlüssels
 * @param publicKey     öffentlicher Schlüssel als eine Zeile im {@code authorized_keys}-Format
 * @param fingerprint   Fingerabdruck {@code SHA256:…}
 * @param createdMillis Zeitpunkt der Anlage in Millisekunden seit 1970
 */
public record SshKeyInfo(
        @NonNull String id,
        @NonNull String label,
        @NonNull SshKeyType type,
        @NonNull String publicKey,
        @NonNull String fingerprint,
        long createdMillis
) {

    public SshKeyInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
