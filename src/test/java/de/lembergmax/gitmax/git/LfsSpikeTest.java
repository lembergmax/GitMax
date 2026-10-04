package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lfs.BuiltinLFS;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Befund des LFS-Spikes (M10). Die App unterstützt Git LFS bewusst <b>nicht</b>; diese Tests halten fest, was JGits
 * eingebauter LFS-Filter kann und was für die Anmeldung nötig wäre, falls es später doch gebraucht wird:
 * <ul>
 *   <li>Der Filter braucht in der Repo-Config {@code filter.lfs.useJGitBuiltin}, {@code required}, {@code clean} und
 *       {@code smudge} (die Werte, die JGits {@code InstallBuiltinLfsCommand} setzt) und ein einmaliges
 *       {@code BuiltinLFS.register()}.</li>
 *   <li>Die LFS-Verbindung nutzt <i>keinen</i> {@code CredentialsProvider}: Ohne Zutun gibt es 401, der Checkout bricht
 *       ganz ab. Zugangsdaten in der URL macht JGit nicht zu einem {@code Authorization}-Header.</li>
 *   <li>Gangbar ist nur eine eigene {@link HttpConnectionFactory}, die den Header für Hosts mit Konto setzt; dann bleiben
 *       Token aus URL und {@code .git/config} heraus. Der Upload läuft beim Push über JGits Pre-Push-Hook.</li>
 * </ul>
 */
public final class LfsSpikeTest {

    private static final String USER = "max";
    private static final String TOKEN = "test-token-max";
    private static final byte[] PAYLOAD = "Dies ist der echte Inhalt einer LFS-Datei.\n".repeat(500)
            .getBytes(StandardCharsets.UTF_8);

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private SystemReader originalReader;
    private HttpConnectionFactory originalFactory;
    private LfsSpikeServer server;
    private File remote;

    @Before
    public void setUp() throws Exception {
        originalReader = SystemReader.getInstance();
        SystemReader.setInstance(new AppSystemReader(originalReader, folder.newFolder("git-home")));
        originalFactory = HttpTransport.getConnectionFactory();
        BuiltinLFS.register();
        server = new LfsSpikeServer(USER, TOKEN);
        final String oid = server.put(PAYLOAD);

        // Ein Remote, in dem big.bin nur ein Zeiger auf das Objekt auf dem LFS-Server ist.
        final File seed = folder.newFolder("seed");
        final TestRepo repo = TestRepo.init(seed);
        repo.commit(".gitattributes", "*.bin filter=lfs diff=lfs merge=lfs -text\n", "Attribute", TestRepo.ANNA);
        repo.commit("big.bin", pointer(oid, PAYLOAD.length), "LFS-Datei", TestRepo.ANNA);
        remote = new File(folder.getRoot(), "remote.git");
        Git.cloneRepository().setURI(seed.toURI().toString()).setBare(true).setDirectory(remote).call().close();
    }

    @After
    public void tearDown() {
        HttpTransport.setConnectionFactory(originalFactory);
        server.close();
        SystemReader.setInstance(originalReader);
    }

    @Test
    public void downloadWithoutCredentialsFailsAndAbortsTheCheckout() throws Exception {
        final JGitInternalException failure = assertThrows(JGitInternalException.class,
                () -> cloneAndCheckout(folder.newFolder("clone-none"), server.lfsUrl()));

        assertTrue(rootMessage(failure), rootMessage(failure).contains("rc=401"));
        assertTrue(server.requests().stream().noneMatch(request -> request.header("Authorization") != null));
    }

    @Test
    public void credentialsInTheUrlAreNotSentByJgit() throws Exception {
        final String withCredentials = server.lfsUrl().replace("http://", "http://" + USER + ":" + TOKEN + "@");

        assertThrows(JGitInternalException.class,
                () -> cloneAndCheckout(folder.newFolder("clone-url"), withCredentials));

        assertTrue(server.requests().stream().noneMatch(request -> request.header("Authorization") != null));
    }

    @Test
    public void connectionFactoryHeaderLetsTheDownloadWorkWithoutSecretsOnDisk() throws Exception {
        HttpTransport.setConnectionFactory(new HeaderFactory(originalFactory));
        final File target = folder.newFolder("clone-factory");

        cloneAndCheckout(target, server.lfsUrl());

        assertEquals(new String(PAYLOAD, StandardCharsets.UTF_8),
                new String(Files.readAllBytes(new File(target, "big.bin").toPath()), StandardCharsets.UTF_8));
        final String config = new String(Files.readAllBytes(new File(target, ".git/config").toPath()),
                StandardCharsets.UTF_8);
        assertFalse(config.contains(TOKEN));
        assertFalse(config.contains("@127.0.0.1"));
    }

    @Test
    public void pushUploadsTheObjectAndCommitsOnlyThePointer() throws Exception {
        HttpTransport.setConnectionFactory(new HeaderFactory(originalFactory));
        final File target = folder.newFolder("clone-push");
        cloneAndCheckout(target, server.lfsUrl());
        final byte[] fresh = ("Neuer Inhalt " + System.nanoTime() + "\n").repeat(300).getBytes(StandardCharsets.UTF_8);
        Files.write(new File(target, "second.bin").toPath(), fresh);

        try (Git git = Git.open(target)) {
            git.add().addFilepattern("second.bin").call();
            git.commit().setMessage("Zweite LFS-Datei").setAuthor(TestRepo.ANNA).setCommitter(TestRepo.ANNA).call();
            git.push().setRemote("origin").call();
        }

        assertTrue(server.has(LfsSpikeServer.sha256(fresh)));
        final String stored = blobAtMain("second.bin");
        assertTrue(stored, stored.startsWith("version https://git-lfs.github.com/spec/v1\n"));
        assertTrue(stored, stored.contains("oid sha256:" + LfsSpikeServer.sha256(fresh)));
    }

    /** Wie die Engine: ohne Checkout klonen, Filter eintragen, dann hart zurücksetzen (schreibt die Dateien). */
    private void cloneAndCheckout(
            final File target,
            final String lfsUrl
    ) throws Exception {
        try (Git git = Git.cloneRepository().setURI(remote.toURI().toString()).setDirectory(target).setNoCheckout(true)
                .call()) {
            final StoredConfig config = git.getRepository().getConfig();
            config.setBoolean("filter", "lfs", "useJGitBuiltin", true);
            config.setBoolean("filter", "lfs", "required", true);
            config.setString("filter", "lfs", "clean", "jgit://builtin/lfs/clean");
            config.setString("filter", "lfs", "smudge", "jgit://builtin/lfs/smudge");
            config.setString("lfs", null, "url", lfsUrl);
            config.save();
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call();
        }
    }

    private String blobAtMain(
            final String path
    ) throws Exception {
        try (Git git = Git.open(remote); RevWalk walk = new RevWalk(git.getRepository())) {
            final Repository repository = git.getRepository();
            final ObjectId head = repository.resolve("main");
            try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, walk.parseCommit(head).getTree())) {
                return new String(repository.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private static String rootMessage(
            final Throwable failure
    ) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }

    private static String pointer(
            final String oid,
            final long size
    ) {
        return "version https://git-lfs.github.com/spec/v1\noid sha256:" + oid + "\nsize " + size + "\n";
    }

    /** Hängt an jede Anfrage an den Test-Server die Anmeldung; so würde die App das Token einspeisen. */
    private static final class HeaderFactory implements HttpConnectionFactory {

        private final HttpConnectionFactory delegate;

        private HeaderFactory(
                final HttpConnectionFactory delegate
        ) {
            this.delegate = delegate;
        }

        @Override
        public HttpConnection create(
                final URL url
        ) throws IOException {
            return decorate(delegate.create(url), url);
        }

        @Override
        public HttpConnection create(
                final URL url,
                final Proxy proxy
        ) throws IOException {
            return decorate(delegate.create(url, proxy), url);
        }

        private static HttpConnection decorate(
                final HttpConnection connection,
                final URL url
        ) {
            if ("127.0.0.1".equals(url.getHost())) {
                final String basic = Base64.getEncoder().encodeToString((USER + ":" + TOKEN).getBytes(StandardCharsets.UTF_8));
                connection.setRequestProperty("Authorization", "Basic " + basic);
            }
            return connection;
        }
    }
}
