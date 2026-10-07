package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import de.lembergmax.gitmax.domain.CloneJournal;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.CloneRequest;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.KnownHost;
import de.lembergmax.gitmax.domain.model.SshKeyInfo;
import de.lembergmax.gitmax.domain.model.SshKeyType;
import de.lembergmax.gitmax.git.ssh.KnownHostsStore;
import de.lembergmax.gitmax.git.ssh.SshKeyStore;
import de.lembergmax.gitmax.git.ssh.SshTransports;
import de.lembergmax.gitmax.storage.KeystoreSecretCipher;
import de.lembergmax.gitmax.storage.SecretVault;
import de.lembergmax.gitmax.storage.SharedPreferencesKeyValueStore;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Prüft SSH auf dem Gerät: Ed25519 und bcrypt-geschützte Schlüssel laufen unter der Android-Laufzeit (und
 * unter R8), der Android-Keystore verwahrt die privaten Schlüssel, und GitHub wird über den veröffentlichten
 * Fingerabdruck erkannt. Nutzt nur öffentliche App-Klassen, damit derselbe Test gegen den R8-Build läuft.
 */
public final class SshDeviceTest {

    /** Wegwerf-Schlüssel mit Passphrase {@code geheim-123}, mit {@code ssh-keygen} erzeugt; er schützt nichts. */
    private static final String PROTECTED_KEY =
            "-----BEGIN OPENSSH PRIVATE KEY-----\n"
                    + "b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAAGAAAABA44XYMXR\n"
                    + "LvirMJBPR5nYPxAAAAGAAAAAEAAAAzAAAAC3NzaC1lZDI1NTE5AAAAIK9p/5er0ePShagO\n"
                    + "AGB8V+9CjgFRXuaJY/cNvAsIHNOoAAAAoKf55rKmPas/EXayMtRxILkrzO75OFETmq8AgI\n"
                    + "cWtx0ZhQmGGxgiKIbTvQhhpT5pth8SZ9+sfI9uRcj367OrC8+d/lpKaB8AcVXF4jPFMSom\n"
                    + "F0ME33JuSYibTTwGOUgkyFhA7EKldjXqLMJhjfYvHTSoZRTH0yZ+4Qhmv6y3yXsx2q7RP0\n"
                    + "EDB6ix8bkdOs7xkFn/PBFSu5fBp5F5RXBKoMo=\n"
                    + "-----END OPENSSH PRIVATE KEY-----\n";
    private static final String PROTECTED_FINGERPRINT = "SHA256:rIpnmNs1MYOaQR7xJWBQu2Tw5ZenvCk5kqpMdNb2+iw";

    private Context context;
    private SharedPreferencesKeyValueStore metadata;
    private SharedPreferencesKeyValueStore secrets;
    private SshKeyStore keys;
    private KnownHostsStore hosts;
    private File base;
    private String suffix;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        JgitEnvironment.install(context);
        suffix = UUID.randomUUID().toString();
        metadata = new SharedPreferencesKeyValueStore(context, "ssh_test_meta_" + suffix);
        secrets = new SharedPreferencesKeyValueStore(context, "ssh_test_secrets_" + suffix);
        keys = new SshKeyStore(metadata, new SecretVault(secrets, new KeystoreSecretCipher()), System::currentTimeMillis,
                () -> UUID.randomUUID().toString());
        hosts = new KnownHostsStore(metadata, System::currentTimeMillis);
        base = new File(context.getCacheDir(), "ssh-test-" + suffix);
        assertTrue(base.mkdirs());
    }

    @After
    public void tearDown() throws IOException {
        for (final SshKeyInfo key : keys.list()) {
            keys.remove(key.id());
        }
        if (base.exists()) {
            try (Stream<java.nio.file.Path> walk = Files.walk(base.toPath())) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
        // Die Tests laufen im Prozess der App: ihre Preferences-Dateien lägen sonst für immer in deren Daten.
        context.deleteSharedPreferences("ssh_test_meta_" + suffix);
        context.deleteSharedPreferences("ssh_test_secrets_" + suffix);
    }

    @Test
    public void ed25519AndRsaKeysAreGeneratedAndKeptInTheKeystoreVault() throws Exception {
        final SshKeyInfo ed25519 = keys.generate("Ed", SshKeyType.ED25519);
        final SshKeyInfo rsa = keys.generate("Rsa", SshKeyType.RSA);

        assertTrue(ed25519.publicKey().startsWith("ssh-ed25519 AAAA"));
        assertTrue(rsa.publicKey().startsWith("ssh-rsa AAAA"));
        assertEquals(2, keys.keyPairs().size());
    }

    @Test
    public void aPassphraseProtectedKeyWithBcryptKdfCanBeImported() throws Exception {
        final SshKeyInfo info = keys.importKey("Geschützt", PROTECTED_KEY, "geheim-123");

        assertEquals(SshKeyType.ED25519, info.type());
        assertEquals(PROTECTED_FINGERPRINT, info.fingerprint());
        // Bei der Verwendung ist keine Passphrase mehr nötig: der Schlüssel liegt im Keystore-Tresor.
        assertEquals(1, keys.keyPairs().size());
    }

    @Test
    public void githubIsRecognizedByItsPublishedFingerprintAndRefusesAnUnregisteredKey() throws Exception {
        keys.generate("Nicht registriert", SshKeyType.ED25519);
        final SshTransports transports = new SshTransports(new File(base, "ssh-home"), keys, hosts);
        final JgitEngine engine = new JgitEngine(GitCredentials.NONE, file -> false, CloneJournal.NONE,
                IdentitySource.NONE, RemoteUrl::toCanonicalUrl, transports);
        final RemoteUrl url = RemoteUrl.parse("git@github.com:octocat/Hello-World.git").orElseThrow();

        try {
            engine.cloneRepository(new CloneRequest(url, new File(base, "klon"), null, false, false), GitProgress.NONE);
            throw new AssertionError("Der Klon mit einem unbekannten Schlüssel darf nicht gelingen");
        } catch (final GitFailureException failure) {
            Assume.assumeFalse("Netzwerk nicht erreichbar: " + failure.getMessage(), failure.kind() == GitFailureKind.NETWORK);
            assertEquals(failure.getMessage(), GitFailureKind.SSH_REJECTED, failure.kind());
        }

        final List<KnownHost> known = hosts.forHost("github.com");
        assertFalse("GitHub wurde nicht erkannt", known.isEmpty());
        assertTrue("Der Fingerabdruck stimmt nicht mit dem veröffentlichten überein", known.get(0).verified());
        assertTrue(hosts.pending("github.com").isEmpty());
        assertFalse(new File(base, "klon").exists());
    }
}
