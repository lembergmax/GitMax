package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.CloneJournal;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.GitFailureKind;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Optional;

/** Prüft das Anlegen eines neuen lokalen Repos durch die Engine. */
public final class JgitInitRepositoryTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private SystemReader originalReader;
    private JgitEngine engine;

    @Before
    public void setUp() throws Exception {
        originalReader = SystemReader.getInstance();
        SystemReader.setInstance(new AppSystemReader(originalReader, folder.newFolder("git-home")));
        engine = new JgitEngine(GitCredentials.NONE, file -> false, CloneJournal.NONE, IdentitySource.NONE);
    }

    @After
    public void tearDown() {
        SystemReader.setInstance(originalReader);
    }

    @Test
    public void initCreatesARepoOnTheChosenBranchWithTheOrigin() throws Exception {
        final File repo = new File(folder.getRoot(), "neu");

        engine.initRepository(repo, "main", "https://github.com/max/neu.git");

        try (Git git = Git.open(repo)) {
            assertEquals("main", git.getRepository().getBranch());
            assertEquals("https://github.com/max/neu.git", git.getRepository().getConfig().getString("remote", "origin", "url"));
            assertEquals("+refs/heads/*:refs/remotes/origin/*", git.getRepository().getConfig().getString("remote", "origin", "fetch"));
        }
    }

    @Test
    public void initWithoutAnOriginCreatesNoRemote() throws Exception {
        final File repo = new File(folder.getRoot(), "lokal");

        engine.initRepository(repo, "main", null);

        try (Git git = Git.open(repo)) {
            assertTrue(git.getRepository().getRemoteNames().isEmpty());
        }
    }

    @Test
    public void credentialsInTheOriginNeverReachTheConfig() throws Exception {
        final File repo = new File(folder.getRoot(), "neu");

        engine.initRepository(repo, "main", "https://max:geheim@example.invalid/max/neu.git");

        try (Git git = Git.open(repo)) {
            final String url = git.getRepository().getConfig().getString("remote", "origin", "url");
            assertEquals("https://example.invalid/max/neu.git", url);
        }
        assertFalse(new String(Files.readAllBytes(new File(repo, ".git/config").toPath())).contains("geheim"));
    }

    @Test
    public void anEmptyOriginIsRefusedAndNothingStaysBehind() {
        final File repo = new File(folder.getRoot(), "neu");

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.initRepository(repo, "main", "  "));

        assertEquals(GitFailureKind.INVALID_NAME, failure.kind());
        assertFalse(repo.exists());
    }

    @Test
    public void aNonEmptyFolderIsRefusedAndLeftUntouched() throws Exception {
        final File repo = folder.newFolder("belegt");
        Files.write(new File(repo, "fremd.txt").toPath(), new byte[] {1});

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.initRepository(repo, "main", null));

        assertEquals(GitFailureKind.ALREADY_EXISTS, failure.kind());
        assertTrue(new File(repo, "fremd.txt").isFile());
        assertFalse(new File(repo, ".git").exists());
    }

    @Test
    public void anEmptyExistingFolderIsUsed() throws Exception {
        final File repo = folder.newFolder("leer");

        engine.initRepository(repo, "main", null);

        assertTrue(new File(repo, ".git").isDirectory());
    }

    @Test
    public void anInvalidBranchNameFailsAndRemovesTheFolder() {
        final File repo = new File(folder.getRoot(), "neu");

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.initRepository(repo, "kein gültiger name", null));

        assertEquals(GitFailureKind.INVALID_NAME, failure.kind());
        assertFalse(repo.exists());
    }

    @Test
    public void onPhoneStorageSymlinksAndFileModeAreSwitchedOff() throws Exception {
        final JgitEngine phone = new JgitEngine(GitCredentials.NONE, file -> true, CloneJournal.NONE, IdentitySource.NONE);
        final File repo = new File(folder.getRoot(), "handy");

        phone.initRepository(repo, "main", null);

        try (Git git = Git.open(repo)) {
            final Repository repository = git.getRepository();
            assertFalse(repository.getConfig().getBoolean("core", "symlinks", true));
            assertFalse(repository.getConfig().getBoolean("core", "filemode", true));
        }
    }

    @Test
    public void theResolvedIdentityIsWrittenIntoTheNewRepo() throws Exception {
        final JgitEngine withIdentity = new JgitEngine(GitCredentials.NONE, file -> false, CloneJournal.NONE,
                ignored -> Optional.of(new CommitIdentity("Max Lemberg", "max@example.invalid")));
        final File repo = new File(folder.getRoot(), "ident");

        withIdentity.initRepository(repo, "main", null);

        try (Git git = Git.open(repo)) {
            assertEquals("Max Lemberg", git.getRepository().getConfig().getString("user", null, "name"));
            assertEquals("max@example.invalid", git.getRepository().getConfig().getString("user", null, "email"));
        }
    }

    @Test
    public void withoutAnIdentitySourceTheConfigGetsNoUser() throws Exception {
        final File repo = new File(folder.getRoot(), "ohne");

        engine.initRepository(repo, "main", null);

        try (Git git = Git.open(repo)) {
            assertNull(git.getRepository().getConfig().getString("user", null, "name"));
        }
    }
}
