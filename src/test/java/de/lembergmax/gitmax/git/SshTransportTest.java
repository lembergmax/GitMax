package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.CloneJournal;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.CloneRequest;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.CommitRequest;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.HostKeyChallenge;
import de.lembergmax.gitmax.domain.model.PushRequest;
import de.lembergmax.gitmax.domain.model.SshKeyInfo;
import de.lembergmax.gitmax.domain.model.SshKeyType;
import de.lembergmax.gitmax.domain.model.UpdateOutcome;
import de.lembergmax.gitmax.domain.model.UpdateRequest;
import de.lembergmax.gitmax.git.ssh.GitSshTestServer;
import de.lembergmax.gitmax.git.ssh.HostIds;
import de.lembergmax.gitmax.git.ssh.KnownHostsStore;
import de.lembergmax.gitmax.git.ssh.SshKeyCodec;
import de.lembergmax.gitmax.git.ssh.SshKeyStore;
import de.lembergmax.gitmax.git.ssh.SshTransports;
import de.lembergmax.gitmax.storage.AesTestCipher;
import de.lembergmax.gitmax.storage.MemoryKeyValueStore;
import de.lembergmax.gitmax.storage.SecretVault;
import de.lembergmax.gitmax.testsupport.GitFixtures;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Klonen, Pushen und Abrufen über SSH gegen einen echten SSH-Server (MINA SSHD, {@link GitSshTestServer}):
 * Schlüsselanmeldung, Vertrauen beim ersten Kontakt, geänderter Server-Schlüssel, abgelehnter Schlüssel.
 */
public final class SshTransportTest {

    private static final CommitIdentity IDENTITY = new CommitIdentity("Max", "max@example.invalid");

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final List<PublicKey> authorized = new ArrayList<>();
    private final AtomicInteger ids = new AtomicInteger();
    private SystemReader originalReader;
    private File serverRoot;
    private File bare;
    private KeyPair hostKey;
    private GitSshTestServer server;
    private SshKeyStore keys;
    private KnownHostsStore hosts;
    private JgitEngine engine;
    private RemoteUrl url;

    @Before
    public void setUp() throws Exception {
        originalReader = SystemReader.getInstance();
        SystemReader.setInstance(new AppSystemReader(originalReader, folder.newFolder("git-home")));
        serverRoot = folder.newFolder("srv");
        bare = GitFixtures.createBareRemote(new File(serverRoot, "max/alpha.git"));
        GitFixtures.seedWithFile(bare, folder.newFolder("scratch"), "README.md", "ssh\n");
        hostKey = SshKeyCodec.generate(SshKeyType.ED25519);
        server = GitSshTestServer.start(serverRoot, 0, this::isAuthorized, hostKey);

        keys = new SshKeyStore(new MemoryKeyValueStore(), new SecretVault(new MemoryKeyValueStore(), new AesTestCipher()),
                () -> 1L, () -> "key-" + ids.incrementAndGet());
        hosts = new KnownHostsStore(new MemoryKeyValueStore(), () -> 1L);
        final SshTransports transports = new SshTransports(folder.newFolder("ssh-home"), keys, hosts);
        engine = new JgitEngine(GitCredentials.NONE, file -> false, CloneJournal.NONE, IdentitySource.NONE, RemoteUrl::toCanonicalUrl, transports);
        url = RemoteUrl.parse("ssh://git@127.0.0.1:" + server.port() + "/max/alpha.git").orElseThrow();
    }

    @After
    public void tearDown() throws Exception {
        server.close();
        SystemReader.setInstance(originalReader);
    }

    @Test
    public void cloneWorksOnceTheServerIsTrustedAndTheKeyIsAuthorized() throws Exception {
        final SshKeyInfo key = authorizedKey();
        trustServer();
        final File target = new File(folder.getRoot(), "klon");

        engine.cloneRepository(new CloneRequest(url, target, null, false, false), GitProgress.NONE);

        assertEquals("ssh\n", read(new File(target, "README.md")));
        assertTrue(key.publicKey().startsWith("ssh-ed25519 "));
    }

    @Test
    public void anUnknownServerStopsTheCloneAndOffersItsFingerprint() throws Exception {
        authorizedKey();
        final File target = new File(folder.getRoot(), "klon");

        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> engine.cloneRepository(new CloneRequest(url, target, null, false, false), GitProgress.NONE));

        final String hostId = HostIds.of("127.0.0.1", server.port());
        assertEquals(GitFailureKind.HOST_KEY_UNKNOWN, failure.kind());
        assertEquals(List.of(hostId), failure.paths());
        final HostKeyChallenge challenge = hosts.pending(hostId).orElseThrow();
        assertEquals(SshKeyCodec.fingerprint(hostKey.getPublic()), challenge.fingerprint());
        assertFalse(challenge.changed());
        assertFalse("Nichts wird vor dem Vertrauen vermerkt", hosts.all().iterator().hasNext());
        assertFalse("Der halbe Klon ist weg", target.exists());
    }

    @Test
    public void afterTrustingTheFingerprintTheSameCloneSucceeds() throws Exception {
        authorizedKey();
        final File target = new File(folder.getRoot(), "klon");
        final CloneRequest request = new CloneRequest(url, target, null, false, false);
        assertThrows(GitFailureException.class, () -> engine.cloneRepository(request, GitProgress.NONE));

        hosts.trust(hosts.pending(HostIds.of("127.0.0.1", server.port())).orElseThrow());
        engine.cloneRepository(request, GitProgress.NONE);

        assertTrue(new File(target, "README.md").isFile());
        assertTrue(hosts.pending(HostIds.of("127.0.0.1", server.port())).isEmpty());
    }

    @Test
    public void aChangedServerKeyIsAHardErrorAndNeverAcceptedAutomatically() throws Exception {
        authorizedKey();
        trustServer();
        server.close();
        hostKey = SshKeyCodec.generate(SshKeyType.ED25519);
        server = GitSshTestServer.start(serverRoot, server.port(), this::isAuthorized, hostKey);
        final File target = new File(folder.getRoot(), "klon");

        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> engine.cloneRepository(new CloneRequest(url, target, null, false, false), GitProgress.NONE));

        assertEquals(GitFailureKind.HOST_KEY_CHANGED, failure.kind());
        final HostKeyChallenge challenge = hosts.pending(HostIds.of("127.0.0.1", server.port())).orElseThrow();
        assertTrue(challenge.changed());
        assertEquals(1, challenge.knownFingerprints().size());
        assertFalse(target.exists());
        // Auch ein zweiter Versuch ändert nichts: ohne bewusste Entscheidung bleibt es gesperrt.
        final GitFailureException again = assertThrows(GitFailureException.class,
                () -> engine.cloneRepository(new CloneRequest(url, target, null, false, false), GitProgress.NONE));
        assertEquals(GitFailureKind.HOST_KEY_CHANGED, again.kind());
    }

    @Test
    public void aKeyTheServerDoesNotKnowIsReportedAsRejected() throws Exception {
        keys.generate("unbekannt", SshKeyType.ED25519);
        trustServer();
        final File target = new File(folder.getRoot(), "klon");

        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> engine.cloneRepository(new CloneRequest(url, target, null, false, false), GitProgress.NONE));

        assertEquals(failure.getMessage(), GitFailureKind.SSH_REJECTED, failure.kind());
        assertFalse(target.exists());
    }

    @Test
    public void withoutAnySshKeyTheOperationSaysSo() {
        final File target = new File(folder.getRoot(), "klon");

        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> engine.cloneRepository(new CloneRequest(url, target, null, false, false), GitProgress.NONE));

        assertEquals(GitFailureKind.SSH_NO_KEY, failure.kind());
    }

    @Test
    public void commitPushAndUpdateWorkOverSsh() throws Exception {
        authorizedKey();
        trustServer();
        final File first = new File(folder.getRoot(), "erster");
        final File second = new File(folder.getRoot(), "zweiter");
        engine.cloneRepository(new CloneRequest(url, first, null, false, false), GitProgress.NONE);
        engine.cloneRepository(new CloneRequest(url, second, null, false, false), GitProgress.NONE);

        Files.write(new File(first, "neu.txt").toPath(), "neu\n".getBytes(StandardCharsets.UTF_8));
        engine.stage(first, List.of("neu.txt"));
        engine.commit(new CommitRequest(first, "Neue Datei", IDENTITY, false));
        engine.push(new PushRequest(first, false, false), GitProgress.NONE);

        final UpdateOutcome outcome = engine.update(new UpdateRequest(second, UpdateRequest.Strategy.MERGE,
                UpdateRequest.ConflictPolicy.ABORT, false), GitProgress.NONE);
        assertEquals(UpdateOutcome.FAST_FORWARDED, outcome);
        assertEquals("neu\n", read(new File(second, "neu.txt")));
        try (Git remote = Git.open(bare)) {
            assertEquals("Neue Datei", remote.log().call().iterator().next().getShortMessage());
        }
    }

    @Test
    public void aRemovedKeyNoLongerOpensTheServer() throws Exception {
        final SshKeyInfo key = authorizedKey();
        trustServer();
        keys.remove(key.id());
        keys.generate("anderer", SshKeyType.ED25519);
        final File target = new File(folder.getRoot(), "klon");

        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> engine.cloneRepository(new CloneRequest(url, target, null, false, false), GitProgress.NONE));

        assertEquals(GitFailureKind.SSH_REJECTED, failure.kind());
    }

    /* ------------------------------------------------------------------------------------------ */

    private boolean isAuthorized(
            final PublicKey presented
    ) {
        return authorized.stream().anyMatch(allowed -> KeyUtils.compareKeys(allowed, presented));
    }

    /** Legt einen Schlüssel an und erlaubt ihn auf dem Server. */
    private SshKeyInfo authorizedKey() throws Exception {
        final SshKeyInfo info = keys.generate("test", SshKeyType.ED25519);
        for (final KeyPair pair : keys.keyPairs()) {
            if (SshKeyCodec.fingerprint(pair.getPublic()).equals(info.fingerprint())) {
                authorized.add(pair.getPublic());
            }
        }
        return info;
    }

    /** Merkt den Schlüssel des Servers, so wie es der Nutzer nach der Rückfrage tut. */
    private void trustServer() throws Exception {
        final String hostId = HostIds.of("127.0.0.1", server.port());
        final String[] parts = PublicKeyEntry.toString(hostKey.getPublic()).split(" ", 2);
        hosts.trust(new HostKeyChallenge(hostId, parts[0], parts[1], SshKeyCodec.fingerprint(hostKey.getPublic()), List.of(), false));
    }

    private static String read(
            final File file
    ) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
