package de.lembergmax.gitmax.storage;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Verwahrt Geheimnisse (Token, SSH-Schlüssel) verschlüsselt in einem {@link KeyValueStore}. Der
 * Schlüsselname fließt als zusätzlich authentifizierte Daten ein: ein Chiffretext lässt sich nicht
 * unter einem anderen Namen unterschieben. Die Speicherdatei ist vom Backup ausgenommen.
 */
public final class SecretVault {

    private static final String KEY_PREFIX = "secret.";

    private final KeyValueStore storage;
    private final SecretCipher cipher;

    public SecretVault(
            @NonNull final KeyValueStore storage,
            @NonNull final SecretCipher cipher
    ) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
    }

    /** Speichert oder ersetzt ein Geheimnis. */
    public void put(
            @NonNull final String name,
            @NonNull final String secret
    ) throws VaultException {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(secret, "secret");
        final byte[] sealed = cipher.encrypt(secret.getBytes(StandardCharsets.UTF_8), aad(name));
        storage.put(KEY_PREFIX + name, Base64.getEncoder().encodeToString(sealed));
    }

    /**
     * Liest ein Geheimnis. Fehlt es, kommt {@link Optional#empty()}; ist es vorhanden, aber nicht
     * lesbar, eine {@link VaultException}.
     */
    @NonNull
    public Optional<String> get(
            @NonNull final String name
    ) throws VaultException {
        Objects.requireNonNull(name, "name");
        final Optional<String> stored = storage.get(KEY_PREFIX + name);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        final byte[] sealed;
        try {
            sealed = Base64.getDecoder().decode(stored.get());
        } catch (final IllegalArgumentException notBase64) {
            throw new VaultException(VaultException.Kind.CORRUPT, "The stored secret is corrupt", notBase64);
        }
        return Optional.of(new String(cipher.decrypt(sealed, aad(name)), StandardCharsets.UTF_8));
    }

    public boolean contains(
            @NonNull final String name
    ) {
        return storage.get(KEY_PREFIX + Objects.requireNonNull(name, "name")).isPresent();
    }

    public void remove(
            @NonNull final String name
    ) {
        storage.remove(KEY_PREFIX + Objects.requireNonNull(name, "name"));
    }

    private static byte[] aad(
            final String name
    ) {
        return name.getBytes(StandardCharsets.UTF_8);
    }
}
