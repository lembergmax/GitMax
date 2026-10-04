package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.CommitInfo;
import de.lembergmax.gitmax.domain.model.ConflictFile;
import de.lembergmax.gitmax.domain.model.ConflictKind;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.LogQuery;
import de.lembergmax.gitmax.domain.model.ResetMode;
import de.lembergmax.gitmax.domain.model.Resolution;
import de.lembergmax.gitmax.domain.model.RunningOperation;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Prüft Konfliktlösung, Fortsetzen angefangener Vorgänge sowie Reset, Revert und Cherry-pick. */
public final class JgitConflictAndRewriteTest {

    private static final CommitIdentity MAX = new CommitIdentity("Max", "max@example.invalid");

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final JgitAdvanced advanced = new JgitAdvanced();
    private SystemReader originalReader;
    private TestRepo repo;

    @Before
    public void setUp() throws Exception {
        originalReader = SystemReader.getInstance();
        SystemReader.setInstance(new AppSystemReader(originalReader, folder.newFolder("git-home")));
        repo = TestRepo.init(folder.newFolder("repo"));
        repo.commit("datei.txt", "eins\nzwei\ndrei\n", "Erste Datei", TestRepo.ANNA);
    }

    @After
    public void tearDown() {
        SystemReader.setInstance(originalReader);
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Laufender Vorgang                                                                           */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void nothingRunsInAFreshRepo() throws Exception {
        assertEquals(RunningOperation.NONE, advanced.runningOperation(repo.directory()));
    }

    @Test
    public void aMergeConflictIsReportedAsRunningMerge() throws Exception {
        divergeOnDatei();
        assertConflictOnMerge();

        assertEquals(RunningOperation.MERGE, advanced.runningOperation(repo.directory()));
    }

    @Test
    public void aRebaseConflictIsReportedAsRunningRebase() throws Exception {
        repo.createBranch("feature");
        repo.commit("datei.txt", "feature\n", "Feature ändert", TestRepo.ANNA);
        repo.checkout("main");
        repo.commit("datei.txt", "main\n", "Main ändert", TestRepo.ANNA);
        repo.checkout("feature");
        assertThrows(GitFailureException.class, () -> advanced.rebase(repo.directory(), "main"));

        assertEquals(RunningOperation.REBASE, advanced.runningOperation(repo.directory()));
    }

    @Test
    public void aCherryPickConflictIsReportedAsRunningCherryPick() throws Exception {
        repo.createBranch("feature");
        repo.commit("datei.txt", "feature\n", "Feature ändert", TestRepo.BERND);
        final String picked = repo.head();
        repo.checkout("main");
        repo.commit("datei.txt", "main\n", "Main ändert", TestRepo.ANNA);
        assertThrows(GitFailureException.class, () -> advanced.cherryPick(repo.directory(), picked));

        assertEquals(RunningOperation.CHERRY_PICK, advanced.runningOperation(repo.directory()));
    }

    @Test
    public void aRevertConflictIsReportedAsRunningRevert() throws Exception {
        repo.commit("datei.txt", "zwei\n", "Zwei", TestRepo.ANNA);
        final String target = repo.head();
        repo.commit("datei.txt", "drei\n", "Drei", TestRepo.ANNA);
        assertThrows(GitFailureException.class, () -> advanced.revert(repo.directory(), target));

        assertEquals(RunningOperation.REVERT, advanced.runningOperation(repo.directory()));
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Konflikte erkennen und lösen                                                                */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void conflictsListTheFileWithItsKindAndMarkers() throws Exception {
        divergeOnDatei();
        assertConflictOnMerge();

        final List<ConflictFile> conflicts = advanced.conflicts(repo.directory());

        assertEquals(List.of(new ConflictFile("datei.txt", ConflictKind.BOTH_MODIFIED, true)), conflicts);
    }

    @Test
    public void noConflictsMeansAnEmptyList() throws Exception {
        assertTrue(advanced.conflicts(repo.directory()).isEmpty());
    }

    @Test
    public void takingOurSideKeepsOurFileAndClearsTheConflict() throws Exception {
        divergeOnDatei();
        assertConflictOnMerge();

        advanced.resolveConflict(repo.directory(), "datei.txt", Resolution.OURS);

        assertEquals("main\n", repo.read("datei.txt"));
        assertTrue(advanced.conflicts(repo.directory()).isEmpty());
    }

    @Test
    public void takingTheirSideReplacesOurFile() throws Exception {
        divergeOnDatei();
        assertConflictOnMerge();

        advanced.resolveConflict(repo.directory(), "datei.txt", Resolution.THEIRS);

        assertEquals("feature\n", repo.read("datei.txt"));
        assertTrue(advanced.conflicts(repo.directory()).isEmpty());
    }

    @Test
    public void keepingBothSidesJoinsThemWithoutMarkers() throws Exception {
        divergeOnDatei();
        assertConflictOnMerge();

        advanced.resolveConflict(repo.directory(), "datei.txt", Resolution.BOTH);

        assertEquals("main\nfeature\n", repo.read("datei.txt"));
        assertTrue(advanced.conflicts(repo.directory()).isEmpty());
    }

    @Test
    public void aFileEditedByHandCanBeMarkedResolved() throws Exception {
        divergeOnDatei();
        assertConflictOnMerge();
        repo.write("datei.txt", "von Hand\n");

        advanced.markResolved(repo.directory(), "datei.txt");

        assertTrue(advanced.conflicts(repo.directory()).isEmpty());
        assertEquals("von Hand\n", repo.read("datei.txt"));
    }

    @Test
    public void whenTheyDeletedTheFileOursKeepsItAndTheirsRemovesIt() throws Exception {
        repo.createBranch("feature");
        repo.remove("datei.txt", "Feature löscht", TestRepo.ANNA);
        repo.checkout("main");
        repo.commit("datei.txt", "geändert\n", "Main ändert", TestRepo.ANNA);
        assertConflictOnMerge();
        assertEquals(ConflictKind.DELETED_BY_THEM, advanced.conflicts(repo.directory()).get(0).kind());

        advanced.resolveConflict(repo.directory(), "datei.txt", Resolution.THEIRS);

        assertFalse(repo.exists("datei.txt"));
        assertTrue(advanced.conflicts(repo.directory()).isEmpty());
    }

    @Test
    public void whenTheyDeletedTheFileTakingOursKeepsOurVersion() throws Exception {
        repo.createBranch("feature");
        repo.remove("datei.txt", "Feature löscht", TestRepo.ANNA);
        repo.checkout("main");
        repo.commit("datei.txt", "geändert\n", "Main ändert", TestRepo.ANNA);
        assertConflictOnMerge();

        advanced.resolveConflict(repo.directory(), "datei.txt", Resolution.OURS);

        assertEquals("geändert\n", repo.read("datei.txt"));
        assertTrue(advanced.conflicts(repo.directory()).isEmpty());
    }

    @Test
    public void whenWeDeletedTheFileTakingTheirsBringsItBack() throws Exception {
        repo.createBranch("feature");
        repo.commit("datei.txt", "feature\n", "Feature ändert", TestRepo.ANNA);
        repo.checkout("main");
        repo.remove("datei.txt", "Main löscht", TestRepo.ANNA);
        assertConflictOnMerge();
        assertEquals(ConflictKind.DELETED_BY_US, advanced.conflicts(repo.directory()).get(0).kind());

        advanced.resolveConflict(repo.directory(), "datei.txt", Resolution.THEIRS);

        assertEquals("feature\n", repo.read("datei.txt"));
        assertTrue(advanced.conflicts(repo.directory()).isEmpty());
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Vorgang fortsetzen                                                                          */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void continuingAfterResolvingCreatesTheMergeCommit() throws Exception {
        divergeOnDatei();
        assertConflictOnMerge();
        advanced.resolveConflict(repo.directory(), "datei.txt", Resolution.BOTH);

        final String shortId = advanced.continueOperation(repo.directory(), MAX);

        final CommitInfo head = advanced.log(repo.directory(), LogQuery.head()).get(0);
        assertTrue(head.isMerge());
        assertTrue(head.id().startsWith(shortId));
        assertEquals("Max", head.authorName());
        assertEquals(RepositoryState.SAFE, stateOf());
    }

    @Test
    public void continuingWithOpenConflictsFailsAndNamesTheFiles() throws Exception {
        divergeOnDatei();
        assertConflictOnMerge();

        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> advanced.continueOperation(repo.directory(), MAX));

        assertEquals(GitFailureKind.CONFLICT, failure.kind());
        assertEquals(List.of("datei.txt"), failure.paths());
        assertEquals(RepositoryState.MERGING, stateOf());
    }

    @Test
    public void continuingWhenNothingIsRunningIsRefused() {
        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> advanced.continueOperation(repo.directory(), MAX));

        assertEquals(GitFailureKind.NOTHING_TO_COMMIT, failure.kind());
    }

    @Test
    public void aRebaseConflictCanBeResolvedAndContinued() throws Exception {
        repo.createBranch("feature");
        repo.commit("datei.txt", "feature\n", "Feature ändert", TestRepo.ANNA);
        repo.checkout("main");
        repo.commit("datei.txt", "main\n", "Main ändert", TestRepo.ANNA);
        repo.checkout("feature");
        final GitFailureException conflict = assertThrows(GitFailureException.class,
                () -> advanced.rebase(repo.directory(), "main"));
        assertEquals(GitFailureKind.CONFLICT, conflict.kind());
        assertTrue(stateOf().isRebasing());

        advanced.resolveConflict(repo.directory(), "datei.txt", Resolution.BOTH);
        advanced.continueOperation(repo.directory(), MAX);

        assertEquals(RepositoryState.SAFE, stateOf());
        assertEquals(List.of("Feature ändert", "Main ändert"), subjects(2));
        assertEquals("main\nfeature\n", repo.read("datei.txt"));
    }

    @Test
    public void skippingARebaseStepDropsThatCommitAndGoesOn() throws Exception {
        repo.createBranch("feature");
        repo.commit("datei.txt", "feature\n", "Feature ändert", TestRepo.ANNA);
        repo.commit("neu.txt", "neu\n", "Feature neu", TestRepo.ANNA);
        repo.checkout("main");
        repo.commit("datei.txt", "main\n", "Main ändert", TestRepo.ANNA);
        repo.checkout("feature");
        assertThrows(GitFailureException.class, () -> advanced.rebase(repo.directory(), "main"));

        advanced.skipRebase(repo.directory());

        assertEquals(RepositoryState.SAFE, stateOf());
        assertEquals(List.of("Feature neu", "Main ändert"), subjects(2));
        assertEquals("main\n", repo.read("datei.txt"));
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Cherry-pick                                                                                 */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void cherryPickTakesOverACommitWithItsAuthor() throws Exception {
        repo.createBranch("feature");
        repo.commit("nur-feature.txt", "x\n", "Feature-Datei", TestRepo.BERND);
        final String picked = repo.head();
        repo.checkout("main");

        advanced.cherryPick(repo.directory(), picked);

        final CommitInfo head = advanced.log(repo.directory(), LogQuery.head()).get(0);
        assertEquals("Feature-Datei", head.subject());
        assertEquals("Bernd", head.authorName());
        assertEquals("x\n", repo.read("nur-feature.txt"));
    }

    @Test
    public void aCherryPickConflictStaysInTheRepoAndCanBeFinished() throws Exception {
        repo.createBranch("feature");
        repo.commit("datei.txt", "feature\n", "Feature ändert", TestRepo.BERND);
        final String picked = repo.head();
        repo.checkout("main");
        repo.commit("datei.txt", "main\n", "Main ändert", TestRepo.ANNA);

        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> advanced.cherryPick(repo.directory(), picked));

        assertEquals(GitFailureKind.CONFLICT, failure.kind());
        assertEquals(List.of("datei.txt"), failure.paths());
        advanced.resolveConflict(repo.directory(), "datei.txt", Resolution.THEIRS);
        advanced.continueOperation(repo.directory(), MAX);
        final CommitInfo head = advanced.log(repo.directory(), LogQuery.head()).get(0);
        assertEquals("Feature ändert", head.subject());
        assertEquals("Bernd", head.authorName());
        assertEquals(RepositoryState.SAFE, stateOf());
    }

    @Test
    public void cherryPickWithLocalChangesInTheWayFails() throws Exception {
        repo.createBranch("feature");
        repo.commit("datei.txt", "feature\n", "Feature ändert", TestRepo.BERND);
        final String picked = repo.head();
        repo.checkout("main");
        repo.write("datei.txt", "lokal\n");

        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> advanced.cherryPick(repo.directory(), picked));

        assertEquals(GitFailureKind.DIRTY_TREE, failure.kind());
        assertEquals("lokal\n", repo.read("datei.txt"));
    }

    @Test
    public void cherryPickOfAnUnknownCommitIsNotFound() {
        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> advanced.cherryPick(repo.directory(), "0123456789012345678901234567890123456789"));

        assertEquals(GitFailureKind.NOT_FOUND, failure.kind());
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Revert                                                                                      */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void revertAddsACommitThatUndoesTheChange() throws Exception {
        repo.commit("datei.txt", "eins\nzwei\ndrei\nvier\n", "Vierte Zeile", TestRepo.ANNA);
        final String target = repo.head();
        repo.commit("andere.txt", "x\n", "Andere Datei", TestRepo.ANNA);

        advanced.revert(repo.directory(), target);

        assertEquals("eins\nzwei\ndrei\n", repo.read("datei.txt"));
        assertTrue(subjects(1).get(0).startsWith("Revert"));
        assertEquals(4, advanced.log(repo.directory(), LogQuery.head()).size());
    }

    @Test
    public void aRevertConflictStaysInTheRepo() throws Exception {
        repo.commit("datei.txt", "zwei\n", "Zwei", TestRepo.ANNA);
        final String target = repo.head();
        repo.commit("datei.txt", "drei\n", "Drei", TestRepo.ANNA);

        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> advanced.revert(repo.directory(), target));

        assertEquals(GitFailureKind.CONFLICT, failure.kind());
        assertEquals(List.of("datei.txt"), failure.paths());
        advanced.resolveConflict(repo.directory(), "datei.txt", Resolution.OURS);
        advanced.continueOperation(repo.directory(), MAX);
        assertEquals(RepositoryState.SAFE, stateOf());
        assertEquals("Max", advanced.log(repo.directory(), LogQuery.head()).get(0).authorName());
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Reset                                                                                       */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void hardResetMovesBranchIndexAndFiles() throws Exception {
        final String first = repo.head();
        repo.commit("datei.txt", "neu\n", "Zweite", TestRepo.ANNA);
        repo.write("datei.txt", "unversioniert geändert\n");

        advanced.reset(repo.directory(), first, ResetMode.HARD);

        assertEquals(first, repo.head());
        assertEquals("eins\nzwei\ndrei\n", repo.read("datei.txt"));
        try (Git git = repo.open()) {
            assertTrue(git.status().call().isClean());
        }
    }

    @Test
    public void mixedResetKeepsTheFilesButUnstagesThem() throws Exception {
        final String first = repo.head();
        repo.commit("datei.txt", "neu\n", "Zweite", TestRepo.ANNA);

        advanced.reset(repo.directory(), first, ResetMode.MIXED);

        assertEquals(first, repo.head());
        assertEquals("neu\n", repo.read("datei.txt"));
        try (Git git = repo.open()) {
            final Status status = git.status().call();
            assertTrue(status.getChanged().isEmpty());
            assertEquals(Set.of("datei.txt"), status.getModified());
        }
    }

    @Test
    public void softResetKeepsTheChangesStaged() throws Exception {
        final String first = repo.head();
        repo.commit("datei.txt", "neu\n", "Zweite", TestRepo.ANNA);

        advanced.reset(repo.directory(), first, ResetMode.SOFT);

        assertEquals(first, repo.head());
        assertEquals("neu\n", repo.read("datei.txt"));
        try (Git git = repo.open()) {
            final Status status = git.status().call();
            assertEquals(Set.of("datei.txt"), status.getChanged());
            assertTrue(status.getModified().isEmpty());
        }
    }

    @Test
    public void resettingToAnUnknownCommitIsNotFoundAndChangesNothing() throws Exception {
        final String before = repo.head();

        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> advanced.reset(repo.directory(), "0123456789012345678901234567890123456789", ResetMode.HARD));

        assertEquals(GitFailureKind.NOT_FOUND, failure.kind());
        assertEquals(before, repo.head());
    }

    @Test
    public void resetAcceptsAbbreviatedIdsAndBranchNames() throws Exception {
        final String first = repo.head();
        repo.commit("datei.txt", "neu\n", "Zweite", TestRepo.ANNA);

        advanced.reset(repo.directory(), first.substring(0, 7), ResetMode.HARD);

        assertEquals(first, repo.head());
    }

    /* ------------------------------------------------------------------------------------------ */

    /** {@code feature} und {@code main} ändern dieselbe Zeile verschieden; zurück auf {@code main}. */
    private void divergeOnDatei() throws Exception {
        repo.createBranch("feature");
        repo.commit("datei.txt", "feature\n", "Feature ändert", TestRepo.ANNA);
        repo.checkout("main");
        repo.commit("datei.txt", "main\n", "Main ändert", TestRepo.ANNA);
    }

    private void assertConflictOnMerge() {
        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> advanced.merge(repo.directory(), "feature"));
        assertEquals(GitFailureKind.CONFLICT, failure.kind());
    }

    private RepositoryState stateOf() throws Exception {
        try (Git git = repo.open()) {
            return git.getRepository().getRepositoryState();
        }
    }

    private List<String> subjects(
            final int count
    ) throws Exception {
        try (Git git = repo.open()) {
            final List<String> subjects = new ArrayList<>();
            for (final RevCommit commit : git.log().setMaxCount(count).call()) {
                subjects.add(commit.getShortMessage());
            }
            return subjects;
        }
    }
}
