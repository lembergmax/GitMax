package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.CloneJournal;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.ChangedFile;
import de.lembergmax.gitmax.domain.model.CloneRequest;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.CommitRequest;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.PushRequest;
import de.lembergmax.gitmax.domain.model.UpdateOutcome;
import de.lembergmax.gitmax.domain.model.UpdateRequest;
import de.lembergmax.gitmax.domain.model.WorkingTree;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Prüft die Engine gegen lokale Bare-Repos. Das Remote heißt für die Engine {@code max/alpha}; die
 * Test-Naht {@code transportUrl} lenkt es auf den Ordner um.
 */
public final class JgitEngineTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.invalid");
    private static final CommitIdentity IDENTITY = new CommitIdentity("Max", "max@example.invalid");
    private static final String FILE_TEXT = "eins\nzwei\ndrei\nvier\nfünf\n";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final List<String> journalLog = new ArrayList<>();
    private SystemReader originalReader;
    private File remote;
    private JgitEngine engine;
    private RemoteUrl url;

    @Before
    public void setUp() throws Exception {
        // Wie in der App: nur die eigene Git-Konfiguration, nicht die des Entwicklerrechners (autocrlf u. a.).
        originalReader = SystemReader.getInstance();
        SystemReader.setInstance(new AppSystemReader(originalReader, folder.newFolder("git-home")));
        remote = new File(folder.getRoot(), "remote.git");
        Git.init().setBare(true).setInitialBranch("main").setDirectory(remote).call().close();
        seedRemote();
        url = RemoteUrl.parse("https://example.invalid/max/alpha.git").orElseThrow();
        engine = new JgitEngine(GitCredentials.NONE, file -> false, new CloneJournal() {
            @Override
            public void begin(final File target) {
                journalLog.add("begin " + target.getName());
            }

            @Override
            public void end(final File target) {
                journalLog.add("end " + target.getName());
            }
        }, IdentitySource.NONE, ignored -> remote.getAbsolutePath());
    }

    @After
    public void tearDown() {
        SystemReader.setInstance(originalReader);
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Klonen                                                                                     */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void cloneChecksOutFilesAndKeepsJournalBalanced() throws Exception {
        final File work = new File(folder.getRoot(), "work");

        engine.cloneRepository(new CloneRequest(url, work, null, false, false), GitProgress.NONE);

        assertEquals(FILE_TEXT, read(work, "datei.txt"));
        assertTrue(new File(work, ".git").isDirectory());
        assertEquals(List.of("begin work", "end work"), journalLog);
        assertTrue(engine.status(work).isClean());
    }

    @Test
    public void cloneIntoNonEmptyFolderFailsAndLeavesItUntouched() throws Exception {
        final File work = new File(folder.getRoot(), "work");
        write(work, "fremd.txt", "gehört mir");

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.cloneRepository(new CloneRequest(url, work, null, false, false), GitProgress.NONE));

        assertEquals(GitFailureKind.ALREADY_EXISTS, failure.kind());
        assertEquals("gehört mir", read(work, "fremd.txt"));
        assertTrue(journalLog.isEmpty());
    }

    @Test
    public void cloneIntoEmptyExistingFolderWorks() throws Exception {
        final File work = folder.newFolder("work");

        engine.cloneRepository(new CloneRequest(url, work, null, false, false), GitProgress.NONE);

        assertEquals(FILE_TEXT, read(work, "datei.txt"));
    }

    @Test
    public void failedCloneRemovesTheTargetFolder() throws Exception {
        final File work = new File(folder.getRoot(), "work");
        final JgitEngine broken = new JgitEngine(GitCredentials.NONE, file -> false, CloneJournal.NONE,
                IdentitySource.NONE, ignored -> new File(folder.getRoot(), "gibt-es-nicht.git").getAbsolutePath());

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                broken.cloneRepository(new CloneRequest(url, work, null, false, false), GitProgress.NONE));

        assertTrue(failure.kind() == GitFailureKind.NOT_FOUND || failure.kind() == GitFailureKind.NOT_A_REPO);
        assertFalse(work.exists());
    }

    @Test
    public void cancelledCloneReportsCancelledAndRemovesTheTargetFolder() throws Exception {
        final File work = new File(folder.getRoot(), "work");
        final GitProgress cancelled = new GitProgress() {
            @Override
            public void onProgress(final Phase phase, final float fraction) {
                // egal
            }

            @Override
            public boolean isCancelled() {
                return true;
            }
        };

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.cloneRepository(new CloneRequest(url, work, null, false, false), cancelled));

        assertEquals(GitFailureKind.CANCELLED, failure.kind());
        assertFalse(work.exists());
        assertEquals(List.of("begin work", "end work"), journalLog);
    }

    @Test
    public void cloneOfNamedBranchChecksItOut() throws Exception {
        final File seed = cloneRaw("seed2");
        try (Git git = Git.open(seed)) {
            git.checkout().setCreateBranch(true).setName("entwurf").call();
            commit(seed, "entwurf.txt", "x", "Entwurf");
            git.push().setRemote("origin").add("entwurf").call();
        }
        final File work = new File(folder.getRoot(), "work");

        engine.cloneRepository(new CloneRequest(url, work, "entwurf", false, false), GitProgress.NONE);

        assertEquals("x", read(work, "entwurf.txt"));
        try (Git git = Git.open(work)) {
            assertEquals("entwurf", git.getRepository().getBranch());
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Update                                                                                     */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void updateWithoutNewCommitsIsUpToDate() throws Exception {
        final File work = engineClone("work");

        assertEquals(UpdateOutcome.UP_TO_DATE, engine.update(merge(work), GitProgress.NONE));
    }

    @Test
    public void updateFastForwardsToNewRemoteCommits() throws Exception {
        final File work = engineClone("work");
        pushFromOther("neu.txt", "neu", "Neu");

        assertEquals(UpdateOutcome.FAST_FORWARDED, engine.update(merge(work), GitProgress.NONE));

        assertEquals("neu", read(work, "neu.txt"));
    }

    @Test
    public void updateMergesDivergedHistories() throws Exception {
        final File work = engineClone("work");
        commit(work, "lokal.txt", "lokal", "Lokal");
        pushFromOther("fern.txt", "fern", "Fern");

        assertEquals(UpdateOutcome.MERGED, engine.update(merge(work), GitProgress.NONE));

        assertEquals("lokal", read(work, "lokal.txt"));
        assertEquals("fern", read(work, "fern.txt"));
        assertEquals(RepositoryState.SAFE, stateOf(work));
    }

    @Test
    public void updateRebasesLocalCommitsOntoRemote() throws Exception {
        final File work = engineClone("work");
        commit(work, "lokal.txt", "lokal", "Lokal");
        pushFromOther("fern.txt", "fern", "Fern");

        final UpdateRequest request = new UpdateRequest(work, UpdateRequest.Strategy.REBASE,
                UpdateRequest.ConflictPolicy.ABORT, false);
        assertEquals(UpdateOutcome.REBASED, engine.update(request, GitProgress.NONE));

        try (Git git = Git.open(work)) {
            final List<String> messages = new ArrayList<>();
            for (final RevCommit commit : git.log().setMaxCount(2).call()) {
                messages.add(commit.getShortMessage());
            }
            assertEquals(List.of("Lokal", "Fern"), messages);
        }
    }

    @Test
    public void fastForwardOnlyRefusesDivergedHistories() throws Exception {
        final File work = engineClone("work");
        commit(work, "lokal.txt", "lokal", "Lokal");
        pushFromOther("fern.txt", "fern", "Fern");

        final UpdateRequest request = new UpdateRequest(work, UpdateRequest.Strategy.FAST_FORWARD_ONLY,
                UpdateRequest.ConflictPolicy.ABORT, false);
        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.update(request, GitProgress.NONE));

        assertEquals(GitFailureKind.NOT_FAST_FORWARD, failure.kind());
        assertFalse(new File(work, "fern.txt").exists());
    }

    @Test
    public void conflictWithAbortLeavesTheRepoUntouched() throws Exception {
        final File work = engineClone("work");
        commit(work, "datei.txt", "lokal\n", "Lokal ändert");
        pushFromOther("datei.txt", "fern\n", "Fern ändert");
        final String headBefore = headOf(work);

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.update(merge(work), GitProgress.NONE));

        assertEquals(GitFailureKind.CONFLICT, failure.kind());
        assertEquals(List.of("datei.txt"), failure.paths());
        assertEquals(RepositoryState.SAFE, stateOf(work));
        assertEquals(headBefore, headOf(work));
        assertEquals("lokal\n", read(work, "datei.txt"));
        assertTrue(engine.status(work).isClean());
    }

    @Test
    public void divergenceWithUnrelatedLocalChangesIsRefusedInsteadOfLosingThemOnAbort() throws Exception {
        final File work = engineClone("work");
        commit(work, "datei.txt", "lokal\n", "Lokal ändert");
        commit(work, "andere.txt", "alt\n", "Andere Datei");
        pushFromOther("datei.txt", "fern\n", "Fern ändert");
        write(work, "andere.txt", "ungesichert\n");
        final String headBefore = headOf(work);

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.update(merge(work), GitProgress.NONE));

        assertEquals("ungesichert\n", read(work, "andere.txt"));
        assertEquals(GitFailureKind.DIRTY_TREE, failure.kind());
        assertEquals(RepositoryState.SAFE, stateOf(work));
        assertEquals(headBefore, headOf(work));
    }

    @Test
    public void autoStashBringsUnrelatedLocalChangesBackAfterAnAbortedConflict() throws Exception {
        final File work = engineClone("work");
        commit(work, "datei.txt", "lokal\n", "Lokal ändert");
        commit(work, "andere.txt", "alt\n", "Andere Datei");
        pushFromOther("datei.txt", "fern\n", "Fern ändert");
        write(work, "andere.txt", "ungesichert\n");
        final String headBefore = headOf(work);

        final UpdateRequest request = new UpdateRequest(work, UpdateRequest.Strategy.MERGE,
                UpdateRequest.ConflictPolicy.ABORT, true);
        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.update(request, GitProgress.NONE));

        assertEquals(GitFailureKind.CONFLICT, failure.kind());
        assertEquals(RepositoryState.SAFE, stateOf(work));
        assertEquals(headBefore, headOf(work));
        assertEquals("lokal\n", read(work, "datei.txt"));
        assertEquals("ungesichert\n", read(work, "andere.txt"));
        try (Git git = Git.open(work)) {
            assertTrue(git.stashList().call().isEmpty());
        }
    }

    @Test
    public void conflictWithLeaveKeepsTheMergeOpen() throws Exception {
        final File work = engineClone("work");
        commit(work, "datei.txt", "lokal\n", "Lokal ändert");
        pushFromOther("datei.txt", "fern\n", "Fern ändert");

        final UpdateRequest request = new UpdateRequest(work, UpdateRequest.Strategy.MERGE,
                UpdateRequest.ConflictPolicy.LEAVE, false);
        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.update(request, GitProgress.NONE));

        assertEquals(GitFailureKind.CONFLICT, failure.kind());
        assertEquals(RepositoryState.MERGING, stateOf(work));
        final WorkingTree tree = engine.status(work);
        assertEquals(List.of(new ChangedFile("datei.txt", ChangedFile.Kind.CONFLICT)), tree.conflicts());
    }

    @Test
    public void abortMergeRestoresTheStateBeforeTheMerge() throws Exception {
        final File work = engineClone("work");
        commit(work, "datei.txt", "lokal\n", "Lokal ändert");
        pushFromOther("datei.txt", "fern\n", "Fern ändert");
        final String headBefore = headOf(work);
        final UpdateRequest request = new UpdateRequest(work, UpdateRequest.Strategy.MERGE,
                UpdateRequest.ConflictPolicy.LEAVE, false);
        assertThrows(GitFailureException.class, () -> engine.update(request, GitProgress.NONE));

        engine.abortMerge(work);

        assertEquals(RepositoryState.SAFE, stateOf(work));
        assertEquals(headBefore, headOf(work));
        assertEquals("lokal\n", read(work, "datei.txt"));
    }

    @Test
    public void rebaseConflictWithAbortRestoresTheBranch() throws Exception {
        final File work = engineClone("work");
        commit(work, "datei.txt", "lokal\n", "Lokal ändert");
        pushFromOther("datei.txt", "fern\n", "Fern ändert");
        final String headBefore = headOf(work);

        final UpdateRequest request = new UpdateRequest(work, UpdateRequest.Strategy.REBASE,
                UpdateRequest.ConflictPolicy.ABORT, false);
        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.update(request, GitProgress.NONE));

        assertEquals(GitFailureKind.CONFLICT, failure.kind());
        assertEquals(RepositoryState.SAFE, stateOf(work));
        assertEquals(headBefore, headOf(work));
    }

    @Test
    public void localChangesInTheWayAreReportedAsDirtyTree() throws Exception {
        final File work = engineClone("work");
        pushFromOther("datei.txt", "fern\nzwei\ndrei\nvier\nfünf\n", "Fern ändert");
        write(work, "datei.txt", "eins\nzwei\ndrei\nvier\nlokal\n");

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.update(merge(work), GitProgress.NONE));

        assertEquals(GitFailureKind.DIRTY_TREE, failure.kind());
        assertEquals("eins\nzwei\ndrei\nvier\nlokal\n", read(work, "datei.txt"));
    }

    @Test
    public void autoStashCarriesLocalChangesAcrossTheUpdate() throws Exception {
        final File work = engineClone("work");
        pushFromOther("datei.txt", "fern\nzwei\ndrei\nvier\nfünf\n", "Fern ändert");
        write(work, "datei.txt", "eins\nzwei\ndrei\nvier\nlokal\n");

        final UpdateRequest request = new UpdateRequest(work, UpdateRequest.Strategy.MERGE,
                UpdateRequest.ConflictPolicy.ABORT, true);
        assertEquals(UpdateOutcome.FAST_FORWARDED, engine.update(request, GitProgress.NONE));

        assertEquals("fern\nzwei\ndrei\nvier\nlokal\n", read(work, "datei.txt"));
        try (Git git = Git.open(work)) {
            assertTrue(git.stashList().call().isEmpty());
        }
    }

    @Test
    public void detachedHeadIsSkipped() throws Exception {
        final File work = engineClone("work");
        try (Git git = Git.open(work)) {
            git.checkout().setName(headOf(work)).call();
        }

        assertEquals(UpdateOutcome.SKIPPED_DETACHED, engine.update(merge(work), GitProgress.NONE));
    }

    @Test
    public void branchWithoutUpstreamIsSkipped() throws Exception {
        final File work = engineClone("work");
        try (Git git = Git.open(work)) {
            git.checkout().setCreateBranch(true).setName("lokal-only").call();
        }

        assertEquals(UpdateOutcome.SKIPPED_NO_UPSTREAM, engine.update(merge(work), GitProgress.NONE));
    }

    @Test
    public void updateOfAFolderThatIsNoRepoFails() throws Exception {
        final File plain = folder.newFolder("kein-repo");

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.update(merge(plain), GitProgress.NONE));

        assertEquals(GitFailureKind.NOT_A_REPO, failure.kind());
    }

    @Test
    public void fetchDoesNotTouchTheWorkingTree() throws Exception {
        final File work = engineClone("work");
        pushFromOther("neu.txt", "neu", "Neu");

        engine.fetch(work, GitProgress.NONE);

        assertFalse(new File(work, "neu.txt").exists());
        try (Git git = Git.open(work)) {
            assertEquals(1, org.eclipse.jgit.lib.BranchTrackingStatus.of(git.getRepository(), "main").getBehindCount());
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Status, Stagen, Commit                                                                     */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void statusGroupsChangesLikeTheCommitScreen() throws Exception {
        final File work = engineClone("work");
        write(work, "datei.txt", "geändert\n");
        write(work, "neu.txt", "neu");
        write(work, "gestaged.txt", "g");
        engine.stage(work, List.of("gestaged.txt"));

        final WorkingTree tree = engine.status(work);

        assertEquals(List.of(new ChangedFile("gestaged.txt", ChangedFile.Kind.ADDED)), tree.staged());
        assertEquals(List.of(new ChangedFile("datei.txt", ChangedFile.Kind.MODIFIED)), tree.unstaged());
        assertEquals(List.of(new ChangedFile("neu.txt", ChangedFile.Kind.UNTRACKED)), tree.untracked());
        assertTrue(tree.conflicts().isEmpty());
    }

    @Test
    public void stageRecordsDeletedFilesAndUnstageTakesThemBack() throws Exception {
        final File work = engineClone("work");
        assertTrue(new File(work, "datei.txt").delete());

        engine.stage(work, List.of("datei.txt"));
        assertEquals(List.of(new ChangedFile("datei.txt", ChangedFile.Kind.DELETED)), engine.status(work).staged());

        engine.unstage(work, List.of("datei.txt"));
        final WorkingTree after = engine.status(work);
        assertTrue(after.staged().isEmpty());
        assertEquals(List.of(new ChangedFile("datei.txt", ChangedFile.Kind.DELETED)), after.unstaged());
    }

    @Test
    public void unstageInAnEmptyRepoRemovesTheFileFromTheIndexOnly() throws Exception {
        final File work = new File(folder.getRoot(), "leer");
        Git.init().setInitialBranch("main").setDirectory(work).call().close();
        write(work, "a.txt", "a");
        engine.stage(work, List.of("a.txt"));

        engine.unstage(work, List.of("a.txt"));

        assertTrue(new File(work, "a.txt").exists());
        assertEquals(List.of(new ChangedFile("a.txt", ChangedFile.Kind.UNTRACKED)), engine.status(work).untracked());
    }

    @Test
    public void commitRecordsTheIdentityAndReturnsAShortId() throws Exception {
        final File work = engineClone("work");
        write(work, "neu.txt", "neu");
        engine.stage(work, List.of("neu.txt"));

        final String id = engine.commit(new CommitRequest(work, "  Neue Datei \n", IDENTITY, false));

        assertEquals(7, id.length());
        try (Git git = Git.open(work)) {
            final RevCommit head = git.log().setMaxCount(1).call().iterator().next();
            assertTrue(head.getName().startsWith(id));
            assertEquals("Neue Datei", head.getFullMessage());
            assertEquals("Max", head.getAuthorIdent().getName());
            assertEquals("max@example.invalid", head.getCommitterIdent().getEmailAddress());
        }
        assertTrue(engine.status(work).isClean());
    }

    @Test
    public void commitWithNothingStagedFails() throws Exception {
        final File work = engineClone("work");

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.commit(new CommitRequest(work, "Nichts", IDENTITY, false)));

        assertEquals(GitFailureKind.NOTHING_TO_COMMIT, failure.kind());
    }

    @Test
    public void commitWithBlankMessageIsRejected() throws Exception {
        final File work = engineClone("work");
        write(work, "neu.txt", "neu");
        engine.stage(work, List.of("neu.txt"));

        assertThrows(IllegalArgumentException.class, () ->
                engine.commit(new CommitRequest(work, "   ", IDENTITY, false)));
    }

    @Test
    public void commitIsBlockedWhileConflictsAreOpen() throws Exception {
        final File work = engineClone("work");
        commit(work, "datei.txt", "lokal\n", "Lokal ändert");
        pushFromOther("datei.txt", "fern\n", "Fern ändert");
        final UpdateRequest leave = new UpdateRequest(work, UpdateRequest.Strategy.MERGE,
                UpdateRequest.ConflictPolicy.LEAVE, false);
        assertThrows(GitFailureException.class, () -> engine.update(leave, GitProgress.NONE));

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.commit(new CommitRequest(work, "Zu früh", IDENTITY, false)));

        assertEquals(GitFailureKind.CONFLICT, failure.kind());
        assertEquals(List.of("datei.txt"), failure.paths());
    }

    @Test
    public void amendReplacesTheLastCommit() throws Exception {
        final File work = engineClone("work");
        write(work, "neu.txt", "neu");
        engine.stage(work, List.of("neu.txt"));
        engine.commit(new CommitRequest(work, "Erster Wurf", IDENTITY, false));

        engine.commit(new CommitRequest(work, "Besser formuliert", IDENTITY, true));

        try (Git git = Git.open(work)) {
            final List<String> messages = new ArrayList<>();
            for (final RevCommit commit : git.log().setMaxCount(2).call()) {
                messages.add(commit.getFullMessage());
            }
            assertEquals(List.of("Besser formuliert", "Datei"), messages);
        }
    }

    @Test
    public void discardRestoresModifiedAndDeletedFilesFromTheLastCommit() throws Exception {
        final File work = engineClone("work");
        commit(work, "zwei.txt", "original\n", "Zweite Datei");
        write(work, "datei.txt", "kaputt\n");
        assertTrue(new File(work, "zwei.txt").delete());

        engine.discard(work, List.of("datei.txt", "zwei.txt"));

        assertEquals(FILE_TEXT, read(work, "datei.txt"));
        assertEquals("original\n", read(work, "zwei.txt"));
        assertTrue(engine.status(work).isClean());
    }

    @Test
    public void discardDropsStagedChangesAsWell() throws Exception {
        final File work = engineClone("work");
        write(work, "datei.txt", "vorgemerkt\n");
        engine.stage(work, List.of("datei.txt"));

        engine.discard(work, List.of("datei.txt"));

        assertEquals(FILE_TEXT, read(work, "datei.txt"));
        assertTrue(engine.status(work).isClean());
    }

    @Test
    public void discardDeletesNewFilesWhetherStagedOrNot() throws Exception {
        final File work = engineClone("work");
        write(work, "neu.txt", "x");
        write(work, "ordner/auch-neu.txt", "y");
        write(work, "vorgemerkt.txt", "z");
        engine.stage(work, List.of("vorgemerkt.txt"));

        engine.discard(work, List.of("neu.txt", "ordner/auch-neu.txt", "vorgemerkt.txt"));

        assertFalse(new File(work, "neu.txt").exists());
        assertFalse(new File(work, "ordner/auch-neu.txt").exists());
        assertFalse(new File(work, "vorgemerkt.txt").exists());
        assertTrue(engine.status(work).isClean());
    }

    @Test
    public void discardLeavesOtherChangesAlone() throws Exception {
        final File work = engineClone("work");
        write(work, "datei.txt", "kaputt\n");
        write(work, "bleibt.txt", "bleibt");

        engine.discard(work, List.of("datei.txt"));

        assertEquals("bleibt", read(work, "bleibt.txt"));
        assertEquals(List.of(new ChangedFile("bleibt.txt", ChangedFile.Kind.UNTRACKED)), engine.status(work).untracked());
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Push                                                                                      */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void pushDeliversLocalCommitsToTheRemote() throws Exception {
        final File work = engineClone("work");
        write(work, "neu.txt", "neu");
        engine.stage(work, List.of("neu.txt"));
        engine.commit(new CommitRequest(work, "Neu", IDENTITY, false));

        engine.push(new PushRequest(work, false, false), GitProgress.NONE);

        assertEquals(headOf(work), remoteHead("main"));
    }

    @Test
    public void pushIsRefusedWhenTheRemoteIsAhead() throws Exception {
        final File work = engineClone("work");
        commit(work, "lokal.txt", "lokal", "Lokal");
        pushFromOther("fern.txt", "fern", "Fern");
        final String remoteBefore = remoteHead("main");

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.push(new PushRequest(work, false, false), GitProgress.NONE));

        assertEquals(GitFailureKind.NOT_FAST_FORWARD, failure.kind());
        assertEquals(remoteBefore, remoteHead("main"));
    }

    @Test
    public void forcePushOverwritesTheRemoteAfterTheLocalSideWasUpdated() throws Exception {
        final File work = engineClone("work");
        commit(work, "lokal.txt", "lokal", "Lokal");

        // Das Remote ist vor dem Fetch des Pushs unverändert, also darf die Lease-Prüfung durchgehen.
        engine.push(new PushRequest(work, false, true), GitProgress.NONE);

        assertEquals(headOf(work), remoteHead("main"));
    }

    @Test
    public void amendingAPushedCommitNeedsAForcePush() throws Exception {
        final File work = engineClone("work");
        commit(work, "lokal.txt", "lokal", "Lokal");
        engine.push(new PushRequest(work, false, false), GitProgress.NONE);
        final String pushed = headOf(work);
        engine.commit(new CommitRequest(work, "Lokal, besser formuliert", IDENTITY, true));
        final String amended = headOf(work);

        final GitFailureException refused = assertThrows(GitFailureException.class, () ->
                engine.push(new PushRequest(work, false, false), GitProgress.NONE));

        assertEquals(GitFailureKind.NOT_FAST_FORWARD, refused.kind());
        assertEquals(pushed, remoteHead("main"));
        engine.push(new PushRequest(work, false, true), GitProgress.NONE);
        assertEquals(amended, remoteHead("main"));
    }

    @Test
    public void forcePushReplacesCommitsThatOnlyTheServerHas() throws Exception {
        final File work = engineClone("work");
        commit(work, "lokal.txt", "lokal", "Lokal");
        pushFromOther("fern.txt", "fern", "Fern");
        final String remoteBefore = remoteHead("main");

        // Wer erzwingt, ersetzt den Server-Stand, auch wenn dort Commits liegen, die lokal fehlen (der Nutzer hat es per
        // Tippbestätigung gewollt). Die Lease-Prüfung schützt nur vor Änderungen zwischen dem Abruf und dem Push.
        engine.push(new PushRequest(work, false, true), GitProgress.NONE);

        assertEquals(headOf(work), remoteHead("main"));
        assertFalse(remoteBefore.equals(remoteHead("main")));
    }

    @Test
    public void pushOfANewBranchSetsTheUpstream() throws Exception {
        final File work = engineClone("work");
        try (Git git = Git.open(work)) {
            git.checkout().setCreateBranch(true).setName("feature").call();
        }
        commit(work, "feature.txt", "f", "Feature");

        engine.push(new PushRequest(work, false, false), GitProgress.NONE);

        assertEquals(headOf(work), remoteHead("feature"));
        try (Git git = Git.open(work)) {
            final Repository repository = git.getRepository();
            assertEquals("origin", repository.getConfig().getString("branch", "feature", "remote"));
            assertEquals("refs/heads/feature", repository.getConfig().getString("branch", "feature", "merge"));
        }
    }

    @Test
    public void pushWithNothingNewSucceeds() throws Exception {
        final File work = engineClone("work");

        engine.push(new PushRequest(work, false, false), GitProgress.NONE);

        assertEquals(headOf(work), remoteHead("main"));
    }

    @Test
    public void pushWithDetachedHeadFails() throws Exception {
        final File work = engineClone("work");
        try (Git git = Git.open(work)) {
            git.checkout().setName(headOf(work)).call();
        }

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.push(new PushRequest(work, false, false), GitProgress.NONE));

        assertEquals(GitFailureKind.NO_UPSTREAM, failure.kind());
    }

    @Test
    public void pushSendsTagsOnRequest() throws Exception {
        final File work = engineClone("work");
        try (Git git = Git.open(work)) {
            git.tag().setName("v1").setAnnotated(false).call();
        }

        engine.push(new PushRequest(work, true, false), GitProgress.NONE);

        try (Git git = Git.open(remote)) {
            assertTrue(git.getRepository().getRefDatabase().exactRef("refs/tags/v1") != null);
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Hilfen                                                                                     */
    /* ------------------------------------------------------------------------------------------ */

    private void seedRemote() throws Exception {
        final File seed = new File(folder.getRoot(), "seed");
        Git.init().setInitialBranch("main").setDirectory(seed).call().close();
        commit(seed, "datei.txt", FILE_TEXT, "Datei");
        try (Git git = Git.open(seed)) {
            git.push().setRemote(remote.getAbsolutePath()).add("main").call();
        }
    }

    private File cloneRaw(final String name) throws Exception {
        final File target = new File(folder.getRoot(), name);
        Git.cloneRepository().setURI(remote.getAbsolutePath()).setDirectory(target).call().close();
        return target;
    }

    private File engineClone(final String name) throws Exception {
        final File target = new File(folder.getRoot(), name);
        engine.cloneRepository(new CloneRequest(url, target, null, false, false), GitProgress.NONE);
        journalLog.clear();
        return target;
    }

    /** Ein anderer Nutzer schickt einen Commit zum Remote. */
    private void pushFromOther(final String path, final String content, final String message) throws Exception {
        final File other = new File(folder.getRoot(), "other");
        if (!other.exists()) {
            cloneRaw("other");
        }
        try (Git git = Git.open(other)) {
            git.pull().call();
        }
        commit(other, path, content, message);
        try (Git git = Git.open(other)) {
            git.push().setRemote("origin").add("main").call();
        }
    }

    private static UpdateRequest merge(final File work) {
        return new UpdateRequest(work, UpdateRequest.Strategy.MERGE, UpdateRequest.ConflictPolicy.ABORT, false);
    }

    private static void commit(final File work, final String path, final String content, final String message)
            throws Exception {
        write(work, path, content);
        try (Git git = Git.open(work)) {
            git.add().addFilepattern(path).call();
            git.commit().setMessage(message).setAuthor(IDENT).setCommitter(IDENT).call();
        }
    }

    private static void write(final File root, final String path, final String content) throws Exception {
        final File file = new File(root, path);
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(final File root, final String path) throws Exception {
        return new String(Files.readAllBytes(new File(root, path).toPath()), StandardCharsets.UTF_8);
    }

    private static String headOf(final File work) throws Exception {
        try (Git git = Git.open(work)) {
            return git.getRepository().resolve("HEAD").getName();
        }
    }

    private String remoteHead(final String branch) throws Exception {
        try (Git git = Git.open(remote)) {
            return git.getRepository().resolve("refs/heads/" + branch).getName();
        }
    }

    private static RepositoryState stateOf(final File work) throws Exception {
        try (Git git = Git.open(work)) {
            return git.getRepository().getRepositoryState();
        }
    }
}
