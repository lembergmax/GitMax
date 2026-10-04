package de.lembergmax.gitmax.git.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.model.SshKeyInfo;
import de.lembergmax.gitmax.domain.model.SshKeyType;
import de.lembergmax.gitmax.storage.KeyValueStore;
import de.lembergmax.gitmax.storage.SecretVault;
import de.lembergmax.gitmax.storage.VaultException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Die SSH-Schlüssel der App. Die Angaben zum Schlüssel (Name, öffentlicher Teil, Fingerabdruck) liegen im
 * normalen Speicher, der private Schlüssel nur im {@link SecretVault}. Es entstehen nie Schlüsseldateien.
 */
public final class SshKeyStore {

    private static final String LIST_KEY = "ssh.keys";
    private static final String SECRET_PREFIX = "ssh.key.";
    private static final int MAX_LABEL = 60;

    private final KeyValueStore store;
    private final SecretVault vault;
    private final LongSupplier clock;
    private final Supplier<String> ids;

    public SshKeyStore(
            @NonNull final KeyValueStore store,
            @NonNull final SecretVault vault,
            @NonNull final LongSupplier clock,
            @NonNull final Supplier<String> ids
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.vault = Objects.requireNonNull(vault, "vault");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    @NonNull
    public synchronized List<SshKeyInfo> list() {
        return read();
    }

    public synchronized boolean isEmpty() {
        return read().isEmpty();
    }

    /** Erzeugt einen neuen Schlüssel und legt ihn ab. */
    @NonNull
    public SshKeyInfo generate(
            @NonNull final String label,
            @NonNull final SshKeyType type
    ) throws SshKeyException {
        if (type == SshKeyType.ECDSA) {
            throw new SshKeyException(SshKeyException.Reason.UNSUPPORTED, "GitMax does not generate ECDSA keys", null);
        }
        // Ein RSA-Schlüssel braucht Sekunden; das geschieht ohne die Sperre, damit Abfragen vom Hauptthread nicht warten.
        final KeyPair pair = SshKeyCodec.generate(type);
        return add(label, pair);
    }

    /**
     * Übernimmt einen vorhandenen privaten Schlüssel. Eine Passphrase wird nur zum Entschlüsseln gebraucht und
     * nicht gespeichert: abgelegt wird der Schlüssel im {@link SecretVault}.
     */
    @NonNull
    public synchronized SshKeyInfo importKey(
            @NonNull final String label,
            @NonNull final String text,
            @Nullable final String passphrase
    ) throws SshKeyException {
        return add(label, SshKeyCodec.decodePrivate(text, passphrase));
    }

    /** Löscht einen Schlüssel samt privatem Teil. */
    public synchronized void remove(
            @NonNull final String id
    ) {
        final List<SshKeyInfo> keys = read();
        if (keys.removeIf(key -> key.id().equals(id))) {
            write(keys);
        }
        vault.remove(SECRET_PREFIX + id);
    }

    /** Alle lesbaren Schlüsselpaare in der Reihenfolge ihrer Anlage; unlesbare werden übersprungen. */
    @NonNull
    public synchronized List<KeyPair> keyPairs() {
        final List<KeyPair> pairs = new ArrayList<>();
        for (final SshKeyInfo info : read()) {
            try {
                final Optional<String> secret = vault.get(SECRET_PREFIX + info.id());
                if (secret.isPresent()) {
                    pairs.add(SshKeyCodec.decodePrivate(secret.get(), null));
                }
            } catch (final VaultException | SshKeyException unreadable) {
                // Ein beschädigter Schlüssel darf die anderen nicht aussperren; er bleibt in der Liste und lässt sich löschen.
                continue;
            }
        }
        return pairs;
    }

    private synchronized SshKeyInfo add(
            final String label,
            final KeyPair pair
    ) throws SshKeyException {
        final String name = label.strip();
        if (name.isEmpty() || name.length() > MAX_LABEL) {
            throw new SshKeyException(SshKeyException.Reason.INVALID_NAME, "The name must be 1 to " + MAX_LABEL + " characters long", null);
        }
        final SshKeyType type = SshKeyCodec.typeOf(pair.getPublic());
        if (type == null) {
            throw new SshKeyException(SshKeyException.Reason.UNSUPPORTED, "This key type is not supported", null);
        }
        final String fingerprint = SshKeyCodec.fingerprint(pair.getPublic());
        final List<SshKeyInfo> keys = read();
        for (final SshKeyInfo existing : keys) {
            if (existing.fingerprint().equals(fingerprint)) {
                throw new SshKeyException(SshKeyException.Reason.DUPLICATE, "This key is already set up: " + existing.label(), null);
            }
        }
        final String id = ids.get();
        try {
            vault.put(SECRET_PREFIX + id, SshKeyCodec.encodePrivate(pair));
        } catch (final VaultException unusable) {
            throw new SshKeyException(SshKeyException.Reason.FAILED, "The key could not be stored securely", unusable);
        }
        final SshKeyInfo info = new SshKeyInfo(id, name, type, SshKeyCodec.publicLine(pair.getPublic(), name),
                fingerprint, clock.getAsLong());
        keys.add(info);
        write(keys);
        return info;
    }

    private List<SshKeyInfo> read() {
        final List<SshKeyInfo> keys = new ArrayList<>();
        final Optional<String> raw = store.get(LIST_KEY);
        if (raw.isEmpty()) {
            return keys;
        }
        try {
            final JSONArray array = new JSONArray(raw.get());
            for (int index = 0; index < array.length(); index += 1) {
                final JSONObject json = array.getJSONObject(index);
                keys.add(new SshKeyInfo(json.getString("id"), json.getString("label"),
                        SshKeyType.valueOf(json.getString("type")), json.getString("public"),
                        json.getString("fingerprint"), json.optLong("created", 0L)));
            }
        } catch (final JSONException | IllegalArgumentException unreadable) {
            return new ArrayList<>();
        }
        return keys;
    }

    private void write(
            final List<SshKeyInfo> keys
    ) {
        try {
            final JSONArray array = new JSONArray();
            for (final SshKeyInfo key : keys) {
                final JSONObject json = new JSONObject();
                json.put("id", key.id());
                json.put("label", key.label());
                json.put("type", key.type().name());
                json.put("public", key.publicKey());
                json.put("fingerprint", key.fingerprint());
                json.put("created", key.createdMillis());
                array.put(json);
            }
            store.put(LIST_KEY, array.toString());
        } catch (final JSONException impossible) {
            throw new IllegalStateException("SSH keys cannot be serialized", impossible);
        }
    }
}
