package de.lembergmax.gitmax.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.CloneJournal;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.NewRepo;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.domain.model.RemoteRepo;
import de.lembergmax.gitmax.git.AppSystemReader;
import de.lembergmax.gitmax.git.GitCredentials;
import de.lembergmax.gitmax.git.GitProgress;
import de.lembergmax.gitmax.git.IdentitySource;
import de.lembergmax.gitmax.git.JgitEngine;
import de.lembergmax.gitmax.provider.ProviderException;
import de.lembergmax.gitmax.provider.ProviderProfile;
import de.lembergmax.gitmax.storage.AesTestCipher;
import de.lembergmax.gitmax.storage.MemoryKeyValueStore;
import de.lembergmax.gitmax.storage.SecretVault;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Prüft das Anlegen eines Repos: Anbieter, lokaler Ordner, README und erster Push. */
public final class RepoCreatorTest {

    private static final String TOKEN = "ghp_geheim";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private SystemReader originalReader;
    private FakeProviderClient client;
    private RepoCreator creator;
    private JgitEngine engine;
    private Account account;
    private File remoteRoot;

    @Before
    public void setUp() throws Exception {
        originalReader = SystemReader.getInstance();
        SystemReader.setInstance(new AppSystemReader(originalReader, folder.newFolder("git-home")));
        remoteRoot = folder.newFolder("remotes");
        client = new FakeProviderClient()
                .returnsProfile(new ProviderProfile("max", 42L, "Max Lemberg", "max@example.org", List.of("repo")))
                .creates(this::createBare);
        final AtomicInteger ids = new AtomicInteger();
        final AccountRepository accounts = new AccountRepository(new MemoryKeyValueStore(),
                new SecretVault(new MemoryKeyValueStore(), new AesTestCipher()), provider -> client, () -> "konto-" + ids.incrementAndGet());
        account = accounts.connect(new AccountEndpoint(ProviderType.GITHUB, "example.invalid", "https://api.example.invalid"), TOKEN);
        creator = new RepoCreator(accounts, provider -> client);
        engine = new JgitEngine(GitCredentials.NONE, file -> false, CloneJournal.NONE, IdentitySource.NONE);
    }

    @After
    public void tearDown() {
        SystemReader.setInstance(originalReader);
    }

    @Test
    public void createsTheRemoteThenTheLocalRepoAndPushesTheReadme() throws Exception {
        final File target = new File(folder.getRoot(), "neu");

        creator.create(request("neu", "Eine Beschreibung", true, true, target), engine, GitProgress.NONE);

        assertEquals(List.of(new NewRepo("neu", "Eine Beschreibung", true)), client.created());
        assertEquals(List.of(TOKEN), client.tokensSeen().subList(client.tokensSeen().size() - 1, client.tokensSeen().size()));
        assertEquals("# neu\n\nEine Beschreibung\n", read(new File(target, "README.md")));
        try (Git local = Git.open(target); Git remote = Git.open(new File(remoteRoot, "neu.git"))) {
            assertEquals(new File(remoteRoot, "neu.git").getAbsolutePath(), local.getRepository().getConfig().getString("remote", "origin", "url"));
            final ObjectId remoteHead = remote.getRepository().resolve("refs/heads/main");
            assertEquals(local.getRepository().resolve("HEAD"), remoteHead);
            final RevCommit commit = remote.log().call().iterator().next();
            assertEquals("Initial commit", commit.getShortMessage());
            assertEquals("Max Lemberg", commit.getAuthorIdent().getName());
            assertEquals("origin", local.getRepository().getConfig().getString("branch", "main", "remote"));
        }
    }

    @Test
    public void withoutADescriptionTheReadmeIsJustTheTitle() throws Exception {
        final File target = new File(folder.getRoot(), "kurz");

        creator.create(request("kurz", "  ", false, true, target), engine, GitProgress.NONE);

        assertEquals("# kurz\n", read(new File(target, "README.md")));
        assertFalse(client.created().get(0).isPrivate());
    }

    @Test
    public void withoutAReadmeTheRemoteStaysEmptyAndNothingIsPushed() throws Exception {
        final File target = new File(folder.getRoot(), "leer");

        creator.create(request("leer", "", true, false, target), engine, GitProgress.NONE);

        assertFalse(new File(target, "README.md").exists());
        try (Git local = Git.open(target); Git remote = Git.open(new File(remoteRoot, "leer.git"))) {
            assertEquals(new File(remoteRoot, "leer.git").getAbsolutePath(), local.getRepository().getConfig().getString("remote", "origin", "url"));
            assertEquals(null, remote.getRepository().resolve("refs/heads/main"));
        }
    }

    @Test
    public void aTakenNameFailsBeforeAnythingIsCreatedLocally() {
        client.failsCreateWith(new ProviderException(ProviderException.Kind.NAME_TAKEN, "Name schon vergeben", null));
        final File target = new File(folder.getRoot(), "neu");

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                creator.create(request("neu", "", true, true, target), engine, GitProgress.NONE));

        assertEquals(GitFailureKind.ALREADY_EXISTS, failure.kind());
        assertFalse(target.exists());
    }

    @Test
    public void providerProblemsBecomeUsefulFailureKinds() {
        assertEquals(GitFailureKind.AUTH, failureKindFor(ProviderException.Kind.UNAUTHORIZED));
        assertEquals(GitFailureKind.AUTH, failureKindFor(ProviderException.Kind.FORBIDDEN));
        assertEquals(GitFailureKind.NETWORK, failureKindFor(ProviderException.Kind.NETWORK));
        assertEquals(GitFailureKind.NETWORK, failureKindFor(ProviderException.Kind.RATE_LIMITED));
        assertEquals(GitFailureKind.INVALID_NAME, failureKindFor(ProviderException.Kind.INVALID));
        assertEquals(GitFailureKind.UNKNOWN, failureKindFor(ProviderException.Kind.MALFORMED));
        assertFalse(new File(folder.getRoot(), "neu").exists());
    }

    @Test
    public void anInvalidNameNeverReachesTheProvider() {
        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                creator.create(request("mein projekt", "", true, true, new File(folder.getRoot(), "x")), engine, GitProgress.NONE));

        assertEquals(GitFailureKind.INVALID_NAME, failure.kind());
        assertTrue(client.created().isEmpty());
    }

    @Test
    public void aTargetThatIsInUseNeverReachesTheProvider() throws Exception {
        final File target = folder.newFolder("belegt");
        Files.write(new File(target, "fremd.txt").toPath(), new byte[] {1});

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                creator.create(request("neu", "", true, true, target), engine, GitProgress.NONE));

        assertEquals(GitFailureKind.ALREADY_EXISTS, failure.kind());
        assertTrue(client.created().isEmpty());
        assertTrue(new File(target, "fremd.txt").isFile());
    }

    @Test
    public void aMissingAccountIsAnAuthProblem() {
        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                creator.create(new RepoCreator.Request("gibt-es-nicht", "neu", "", true, true, new File(folder.getRoot(), "neu")),
                        engine, GitProgress.NONE));

        assertEquals(GitFailureKind.AUTH, failure.kind());
        assertTrue(client.created().isEmpty());
    }

    @Test
    public void retryingAfterAFailedPushContinuesInsteadOfCreatingTwice() throws Exception {
        // Ein früherer Versuch kam bis zum Push: lokal liegt ein Repo mit dem Remote dieses Kontos.
        final File target = new File(folder.getRoot(), "neu");
        try (Git git = Git.init().setDirectory(target).setInitialBranch("main").call()) {
            git.remoteAdd().setName("origin").setUri(new URIish("https://example.invalid/max/neu.git")).call();
        }
        client.failsCreateWith(new ProviderException(ProviderException.Kind.NAME_TAKEN, "Name schon vergeben", null));

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                creator.create(request("neu", "Text", true, true, target), engine, GitProgress.NONE));

        assertTrue("Der Anbieter wird nicht noch einmal gefragt", client.created().isEmpty());
        assertEquals("Der Push scheitert an der unerreichbaren Adresse, nicht am Namen", GitFailureKind.NETWORK, failure.kind());
        assertEquals("# neu\n\nText\n", read(new File(target, "README.md")));
        try (Git git = Git.open(target)) {
            assertEquals("Initial commit", git.log().call().iterator().next().getShortMessage());
        }
    }

    /* ------------------------------------------------------------------------------------------ */

    private GitFailureKind failureKindFor(
            final ProviderException.Kind kind
    ) {
        client.failsCreateWith(new ProviderException(kind, "x", null));
        return assertThrows(GitFailureException.class, () ->
                creator.create(request("neu", "", true, true, new File(folder.getRoot(), "neu")), engine, GitProgress.NONE)).kind();
    }

    private RepoCreator.Request request(
            final String name,
            final String description,
            final boolean isPrivate,
            final boolean withReadme,
            final File target
    ) {
        return new RepoCreator.Request(account.id(), name, description, isPrivate, withReadme, target);
    }

    /** Der „Anbieter“ legt ein Bare-Repo an und gibt dessen Pfad als Klon-Adresse zurück. */
    private RemoteRepo createBare(
            final NewRepo request
    ) {
        try {
            final File bare = new File(remoteRoot, request.name() + ".git");
            Git.init().setBare(true).setInitialBranch("main").setDirectory(bare).call().close();
            return new RemoteRepo(account == null ? "konto-1" : account.id(), "1", request.name(), "max/" + request.name(),
                    request.description(), request.isPrivate(), false, false, "main", bare.getAbsolutePath(), "", "", 0L);
        } catch (final Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String read(
            final File file
    ) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
