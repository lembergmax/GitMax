package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Ein SSH-Server, dessen Schlüssel der Nutzer bestätigt hat (oder der mit dem veröffentlichten Wert des
 * Anbieters übereinstimmt).
 *
 * @param host        Kennung des Servers: {@code github.com} oder {@code [host]:port}
 * @param keyType     Name der Schlüsselart im SSH-Protokoll, z. B. {@code ssh-ed25519}
 * @param keyBase64   öffentlicher Schlüssel des Servers, Base64
 * @param fingerprint Fingerabdruck {@code SHA256:…}
 * @param verified    stimmt mit dem vom Anbieter veröffentlichten Fingerabdruck überein
 * @param addedMillis Zeitpunkt des Vertrauens in Millisekunden seit 1970
 */
public record KnownHost(
        @NonNull String host,
        @NonNull String keyType,
        @NonNull String keyBase64,
        @NonNull String fingerprint,
        boolean verified,
        long addedMillis
) {

    public KnownHost {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(keyType, "keyType");
        Objects.requireNonNull(keyBase64, "keyBase64");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
