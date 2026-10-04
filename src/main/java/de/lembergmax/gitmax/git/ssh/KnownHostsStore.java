package de.lembergmax.gitmax.git.ssh;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.HostKeyChallenge;
import de.lembergmax.gitmax.domain.model.KnownHost;
import de.lembergmax.gitmax.storage.KeyValueStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Die SSH-Server, denen GitMax vertraut (Trust-on-first-use), und der Schlüssel, der den letzten Vorgang
 * scheitern ließ. Ein Server gilt je Schlüsselart: Wechselt der Schlüssel einer bekannten Art, ist das ein
 * harter Fehler und wird nie von selbst akzeptiert. Nur ein vom Anbieter veröffentlichter Fingerabdruck
 * (siehe {@link PublishedHostKeys}) wird ohne Rückfrage angenommen.
 */
public final class KnownHostsStore {

    /** Ergebnis der Prüfung eines vorgelegten Schlüssels. */
    public enum Verdict {
        /** Genau dieser Schlüssel ist bekannt. */
        KNOWN,
        /** Der Schlüssel stimmt mit dem veröffentlichten Wert des Anbieters überein und wurde vermerkt. */
        TRUSTED_BY_PUBLICATION,
        /** Neuer Server: der Nutzer muss den Fingerabdruck bestätigen. */
        UNKNOWN,
        /** Der Server legt für eine bekannte Schlüsselart einen anderen Schlüssel vor. */
        CHANGED
    }

    private static final String HOSTS_KEY = "ssh.hosts";
    private static final String PENDING_KEY = "ssh.pending";

    private final KeyValueStore store;
    private final LongSupplier clock;

    public KnownHostsStore(
            @NonNull final KeyValueStore store,
            @NonNull final LongSupplier clock
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @NonNull
    public synchronized List<KnownHost> all() {
        return readHosts();
    }

    @NonNull
    public synchronized List<KnownHost> forHost(
            @NonNull final String hostId
    ) {
        final List<KnownHost> matching = new ArrayList<>();
        for (final KnownHost known : readHosts()) {
            if (known.host().equals(hostId)) {
                matching.add(known);
            }
        }
        return matching;
    }

    /**
     * Prüft den vorgelegten Schlüssel. Bei {@link Verdict#UNKNOWN} und {@link Verdict#CHANGED} merkt sich der
     * Speicher die Herausforderung, damit die Oberfläche sie nach dem Fehlschlag zeigen kann.
     */
    @NonNull
    public synchronized Verdict evaluate(
            @NonNull final String hostId,
            @NonNull final String keyType,
            @NonNull final String keyBase64,
            @NonNull final String fingerprint
    ) {
        final List<KnownHost> hosts = readHosts();
        final List<String> sameTypeFingerprints = new ArrayList<>();
        for (final KnownHost known : hosts) {
            if (!known.host().equals(hostId) || !known.keyType().equals(keyType)) {
                continue;
            }
            if (known.keyBase64().equals(keyBase64)) {
                clearPending(hostId);
                return Verdict.KNOWN;
            }
            sameTypeFingerprints.add(known.fingerprint());
        }
        final String hostname = HostIds.hostname(hostId);
        final boolean publishedHost = PublishedHostKeys.covers(hostname) && hostId.equals(hostname);
        if (publishedHost && PublishedHostKeys.matches(hostname, fingerprint)) {
            hosts.removeIf(known -> known.host().equals(hostId) && known.keyType().equals(keyType));
            hosts.add(new KnownHost(hostId, keyType, keyBase64, fingerprint, true, clock.getAsLong()));
            writeHosts(hosts);
            clearPending(hostId);
            return Verdict.TRUSTED_BY_PUBLICATION;
        }
        putPending(new HostKeyChallenge(hostId, keyType, keyBase64, fingerprint, sameTypeFingerprints, publishedHost));
        return sameTypeFingerprints.isEmpty() ? Verdict.UNKNOWN : Verdict.CHANGED;
    }

    /** Vertraut dem vorgelegten Schlüssel; ein früherer Schlüssel derselben Art für diesen Server wird ersetzt. */
    public synchronized void trust(
            @NonNull final HostKeyChallenge challenge
    ) {
        final List<KnownHost> hosts = readHosts();
        hosts.removeIf(known -> known.host().equals(challenge.host()) && known.keyType().equals(challenge.keyType()));
        hosts.add(new KnownHost(challenge.host(), challenge.keyType(), challenge.keyBase64(), challenge.fingerprint(),
                false, clock.getAsLong()));
        writeHosts(hosts);
        clearPending(challenge.host());
    }

    /** Vergisst den Schlüssel eines Servers, z. B. nach einem gewollten Schlüsselwechsel. */
    public synchronized void remove(
            @NonNull final String hostId,
            @NonNull final String fingerprint
    ) {
        final List<KnownHost> hosts = readHosts();
        hosts.removeIf(known -> known.host().equals(hostId) && known.fingerprint().equals(fingerprint));
        writeHosts(hosts);
    }

    /** Vergisst alle Schlüssel eines Servers. */
    public synchronized void removeHost(
            @NonNull final String hostId
    ) {
        final List<KnownHost> hosts = readHosts();
        hosts.removeIf(known -> known.host().equals(hostId));
        writeHosts(hosts);
    }

    /** Die offene Herausforderung für diesen Server, wenn der letzte Vorgang an seinem Schlüssel scheiterte. */
    @NonNull
    public synchronized Optional<HostKeyChallenge> pending(
            @NonNull final String hostId
    ) {
        for (final HostKeyChallenge challenge : readPending()) {
            if (challenge.host().equals(hostId)) {
                return Optional.of(challenge);
            }
        }
        return Optional.empty();
    }

    public synchronized void clearPending(
            @NonNull final String hostId
    ) {
        final List<HostKeyChallenge> pending = readPending();
        if (pending.removeIf(challenge -> challenge.host().equals(hostId))) {
            writePending(pending);
        }
    }

    private void putPending(
            final HostKeyChallenge challenge
    ) {
        final List<HostKeyChallenge> pending = readPending();
        pending.removeIf(existing -> existing.host().equals(challenge.host()));
        pending.add(challenge);
        writePending(pending);
    }

    /* ------------------------------------------------------------------------------------------ */

    private List<KnownHost> readHosts() {
        final List<KnownHost> hosts = new ArrayList<>();
        final Optional<String> raw = store.get(HOSTS_KEY);
        if (raw.isEmpty()) {
            return hosts;
        }
        try {
            final JSONArray array = new JSONArray(raw.get());
            for (int index = 0; index < array.length(); index += 1) {
                final JSONObject json = array.getJSONObject(index);
                hosts.add(new KnownHost(json.getString("host"), json.getString("type"), json.getString("key"),
                        json.getString("fp"), json.optBoolean("verified", false), json.optLong("added", 0L)));
            }
        } catch (final JSONException unreadable) {
            // Ein beschädigter Eintrag heißt: die Server sind unbekannt und werden neu bestätigt.
            return new ArrayList<>();
        }
        return hosts;
    }

    private void writeHosts(
            final List<KnownHost> hosts
    ) {
        try {
            final JSONArray array = new JSONArray();
            for (final KnownHost known : hosts) {
                final JSONObject json = new JSONObject();
                json.put("host", known.host());
                json.put("type", known.keyType());
                json.put("key", known.keyBase64());
                json.put("fp", known.fingerprint());
                json.put("verified", known.verified());
                json.put("added", known.addedMillis());
                array.put(json);
            }
            store.put(HOSTS_KEY, array.toString());
        } catch (final JSONException impossible) {
            throw new IllegalStateException("Known servers cannot be serialized", impossible);
        }
    }

    private List<HostKeyChallenge> readPending() {
        final List<HostKeyChallenge> pending = new ArrayList<>();
        final Optional<String> raw = store.get(PENDING_KEY);
        if (raw.isEmpty()) {
            return pending;
        }
        try {
            final JSONArray array = new JSONArray(raw.get());
            for (int index = 0; index < array.length(); index += 1) {
                final JSONObject json = array.getJSONObject(index);
                final List<String> known = new ArrayList<>();
                final JSONArray knownJson = json.getJSONArray("known");
                for (int position = 0; position < knownJson.length(); position += 1) {
                    known.add(knownJson.getString(position));
                }
                pending.add(new HostKeyChallenge(json.getString("host"), json.getString("type"), json.getString("key"),
                        json.getString("fp"), known, json.optBoolean("contradicts", false)));
            }
        } catch (final JSONException unreadable) {
            return new ArrayList<>();
        }
        return pending;
    }

    private void writePending(
            final List<HostKeyChallenge> pending
    ) {
        try {
            final JSONArray array = new JSONArray();
            for (final HostKeyChallenge challenge : pending) {
                final JSONObject json = new JSONObject();
                json.put("host", challenge.host());
                json.put("type", challenge.keyType());
                json.put("key", challenge.keyBase64());
                json.put("fp", challenge.fingerprint());
                json.put("known", new JSONArray(challenge.knownFingerprints()));
                json.put("contradicts", challenge.contradictsPublished());
                array.put(json);
            }
            store.put(PENDING_KEY, array.toString());
        } catch (final JSONException impossible) {
            throw new IllegalStateException("Pending server keys cannot be serialized", impossible);
        }
    }
}
