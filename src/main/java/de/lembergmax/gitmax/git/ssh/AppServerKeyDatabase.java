package de.lembergmax.gitmax.git.ssh;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.HostKeyRejectedException;
import de.lembergmax.gitmax.domain.model.HostKeyChallenge;
import de.lembergmax.gitmax.domain.model.KnownHost;

import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Prüft die Schlüssel der SSH-Server gegen den {@link KnownHostsStore}. Es gibt keine Rückfrage mitten in der
 * Verbindung: ein unbekannter oder geänderter Schlüssel bricht den Vorgang mit einer
 * {@link HostKeyRejectedException} ab, die Oberfläche zeigt danach den Fingerabdruck, und der Nutzer
 * wiederholt den Vorgang, nachdem er vertraut hat. Ein geänderter Schlüssel wird nie von selbst angenommen.
 */
public final class AppServerKeyDatabase implements ServerKeyDatabase {

    private final KnownHostsStore hosts;

    public AppServerKeyDatabase(
            @NonNull final KnownHostsStore hosts
    ) {
        this.hosts = Objects.requireNonNull(hosts, "hosts");
    }

    /** Die bekannten Schlüssel des Servers; JGit bevorzugt damit Schlüsselarten, die schon bekannt sind. */
    @NonNull
    @Override
    public List<PublicKey> lookup(
            @NonNull final String connectAddress,
            @NonNull final InetSocketAddress remoteAddress,
            @NonNull final Configuration config
    ) {
        final List<PublicKey> keys = new ArrayList<>();
        for (final KnownHost known : hosts.forHost(HostIds.parse(connectAddress))) {
            try {
                keys.add(PublicKeyEntry.parsePublicKeyEntry(known.keyType() + " " + known.keyBase64())
                        .resolvePublicKey(null, Collections.emptyMap(), PublicKeyEntryResolver.IGNORING));
            } catch (final IOException | GeneralSecurityException | IllegalArgumentException unreadable) {
                // Ein unlesbarer Eintrag zählt als unbekannt und wird bei der nächsten Prüfung neu bestätigt.
                continue;
            }
        }
        return keys;
    }

    @Override
    public boolean accept(
            @NonNull final String connectAddress,
            @NonNull final InetSocketAddress remoteAddress,
            @NonNull final PublicKey serverKey,
            @NonNull final Configuration config,
            final CredentialsProvider provider
    ) {
        final String hostId = HostIds.parse(connectAddress);
        final String[] parts = PublicKeyEntry.toString(serverKey).split(" ", 2);
        final String keyType = parts[0];
        final String keyBase64 = parts.length > 1 ? parts[1] : "";
        final KnownHostsStore.Verdict verdict = hosts.evaluate(hostId, keyType, keyBase64, SshKeyCodec.fingerprint(serverKey));
        if (verdict == KnownHostsStore.Verdict.KNOWN || verdict == KnownHostsStore.Verdict.TRUSTED_BY_PUBLICATION) {
            return true;
        }
        final HostKeyChallenge challenge = hosts.pending(hostId).orElseThrow();
        throw new HostKeyRejectedException(challenge);
    }
}
