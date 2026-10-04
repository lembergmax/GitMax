package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.RepoHealth;
import de.lembergmax.gitmax.domain.model.RepoStatus;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class RepoStatusReaderTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.invalid");

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final RepoStatusReader reader = new RepoStatusReader();
    private File remote;

    @Before
    public void setUp() throws Exception {
        remote = new File(folder.getRoot(), "remote.git");
        Git.init().setBare(true).setInitialBranch("main").setDirectory(remote).call().close();
    }

    @Test
    public void cleanRepoWithUpstreamIsClean() throws Exception {
        final File work = cloneWithInitialCommit("a");

        final RepoStatus status = reader.full(work);

        assertEquals("main", status.branch());
        assertTrue(status.hasUpstream());
        assertEquals(0, status.ahead());
        assertEquals(0, status.behind());
        assertEquals(0, status.changed());
        assertEquals(RepoHealth.CLEAN, status.health());
    }

    @Test
    public void quickDoesNotCountFiles() throws Exception {
        final File work = cloneWithInitialCommit("a");
        write(work, "neu.txt", "x");

        final RepoStatus status = reader.quick(work);

        assertFalse(status.changesKnown());
        assertEquals(RepoHealth.CLEAN, status.health());
    }

    @Test
    public void modifiedAndUntrackedFilesCountAsChanged() throws Exception {
        final File work = cloneWithInitialCommit("a");
        write(work, "datei.txt", "geändert");
        write(work, "neu.txt", "x");

        final RepoStatus status = reader.full(work);

        assertEquals(2, status.changed());
        assertEquals(RepoHealth.CHANGED, status.health());
    }

    @Test
    public void localCommitMakesTheRepoAhead() throws Exception {
        final File work = cloneWithInitialCommit("a");
        commit(work, "lokal.txt", "x", "lokal");

        final RepoStatus status = reader.full(work);

        assertEquals(1, status.ahead());
        assertEquals(RepoHealth.AHEAD, status.health());
    }

    @Test
    public void remoteCommitAfterFetchMakesTheRepoBehind() throws Exception {
        final File work = cloneWithInitialCommit("a");
        final File other = cloneRemote("b");
        commit(other, "von-anderswo.txt", "x", "remote");
        try (Git git = Git.open(other)) {
            git.push().call();
        }
        try (Git git = Git.open(work)) {
            git.fetch().call();
        }

        final RepoStatus status = reader.full(work);

        assertEquals(1, status.behind());
        assertEquals(RepoHealth.BEHIND, status.health());
    }

    @Test
    public void bothSidesAheadIsDiverged() throws Exception {
        final File work = cloneWithInitialCommit("a");
        final File other = cloneRemote("b");
        commit(other, "remote.txt", "x", "remote");
        try (Git git = Git.open(other)) {
            git.push().call();
        }
        commit(work, "lokal.txt", "y", "lokal");
        try (Git git = Git.open(work)) {
            git.fetch().call();
        }

        final RepoStatus status = reader.full(work);

        assertEquals(1, status.ahead());
        assertEquals(1, status.behind());
        assertEquals(RepoHealth.DIVERGED, status.health());
    }

    @Test
    public void uncommittedChangesTakePriorityOverAhead() throws Exception {
        final File work = cloneWithInitialCommit("a");
        commit(work, "lokal.txt", "x", "lokal");
        write(work, "lokal.txt", "weiter");

        assertEquals(RepoHealth.CHANGED, reader.full(work).health());
    }

    @Test
    public void mergeConflictIsReported() throws Exception {
        final File work = cloneWithInitialCommit("a");
        try (Git git = Git.open(work)) {
            git.checkout().setCreateBranch(true).setName("zweig").call();
            commit(work, "datei.txt", "zweig-version", "zweig");
            git.checkout().setName("main").call();
            commit(work, "datei.txt", "main-version", "main");
            final MergeResult result = git.merge().include(git.getRepository().resolve("zweig")).call();
            assertEquals(MergeResult.MergeStatus.CONFLICTING, result.getMergeStatus());
        }

        final RepoStatus status = reader.full(work);

        assertTrue(status.conflicted());
        assertEquals(RepoHealth.CONFLICT, status.health());
    }

    @Test
    public void detachedHeadReportsTheShortCommitId() throws Exception {
        final File work = cloneWithInitialCommit("a");
        try (Git git = Git.open(work)) {
            final RevCommit head = git.log().call().iterator().next();
            git.checkout().setName(head.getName()).call();
        }

        final RepoStatus status = reader.quick(work);

        assertTrue(status.detached());
        assertFalse(status.hasUpstream());
        assertEquals(7, status.branch().length());
    }

    @Test
    public void branchWithoutUpstreamHasNoCounts() throws Exception {
        final File work = cloneWithInitialCommit("a");
        try (Git git = Git.open(work)) {
            git.checkout().setCreateBranch(true).setName("lokal-nur").call();
        }

        final RepoStatus status = reader.quick(work);

        assertFalse(status.hasUpstream());
        assertEquals(0, status.ahead());
        assertEquals("lokal-nur", status.branch());
    }

    @Test
    public void repoWithoutCommitsIsReadable() throws Exception {
        final File work = new File(folder.getRoot(), "leer");
        Git.init().setInitialBranch("main").setDirectory(work).call().close();
        write(work, "erste.txt", "x");

        final RepoStatus status = reader.full(work);

        assertEquals("main", status.branch());
        assertEquals(1, status.changed());
    }

    private File cloneWithInitialCommit(
            final String name
    ) throws Exception {
        final File work = cloneRemote(name);
        commit(work, "datei.txt", "start", "initial");
        try (Git git = Git.open(work)) {
            git.push().setRemote("origin").add("main").call();
            git.branchCreate().setName("main").setForce(true).setStartPoint("origin/main").call();
        } catch (final Exception notYetTracking) {
            // Beim ersten Push gibt es origin/main noch nicht; Tracking wird unten gesetzt.
        }
        try (Git git = Git.open(work)) {
            final org.eclipse.jgit.lib.StoredConfig config = git.getRepository().getConfig();
            config.setString("branch", "main", "remote", "origin");
            config.setString("branch", "main", "merge", "refs/heads/main");
            config.save();
            git.fetch().call();
        }
        return work;
    }

    private File cloneRemote(
            final String name
    ) throws Exception {
        final File work = new File(folder.getRoot(), name);
        try (Git git = Git.cloneRepository().setURI(remote.toURI().toString()).setDirectory(work).call()) {
            git.getRepository().getConfig().setString("user", null, "name", "Test");
            git.getRepository().getConfig().setString("user", null, "email", "test@example.invalid");
            git.getRepository().getConfig().save();
        }
        return work;
    }

    private static void commit(
            final File work,
            final String file,
            final String content,
            final String message
    ) throws Exception {
        write(work, file, content);
        try (Git git = Git.open(work)) {
            git.add().addFilepattern(file).call();
            git.commit().setMessage(message).setAuthor(IDENT).setCommitter(IDENT).call();
        }
    }

    private static void write(
            final File work,
            final String file,
            final String content
    ) throws Exception {
        Files.write(new File(work, file).toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
