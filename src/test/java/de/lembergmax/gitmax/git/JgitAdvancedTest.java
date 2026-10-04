package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.BranchInfo;
import de.lembergmax.gitmax.domain.model.CommitDetail;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.CommitInfo;
import de.lembergmax.gitmax.domain.model.DiffLine;
import de.lembergmax.gitmax.domain.model.FileChange;
import de.lembergmax.gitmax.domain.model.FileDiff;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.LogQuery;
import de.lembergmax.gitmax.domain.model.RefLabel;
import de.lembergmax.gitmax.domain.model.RemoteInfo;
import de.lembergmax.gitmax.domain.model.StashInfo;
import de.lembergmax.gitmax.domain.model.TagInfo;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RepositoryState;
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
import java.util.stream.Collectors;

/** Prüft die erweiterten Git-Funktionen gegen lokale Repos. */
public final class JgitAdvancedTest {

    private static final PersonIdent AUTHOR = new PersonIdent("Anna", "anna@example.invalid");
    private static final PersonIdent OTHER = new PersonIdent("Bernd", "bernd@example.invalid");
    private static final CommitIdentity TAGGER = new CommitIdentity("Max", "max@example.invalid");

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final JgitAdvanced advanced = new JgitAdvanced();
    private SystemReader originalReader;
    private File repo;

    @Before
    public void setUp() throws Exception {
        originalReader = SystemReader.getInstance();
        SystemReader.setInstance(new AppSystemReader(originalReader, folder.newFolder("git-home")));
        repo = folder.newFolder("repo");
        Git.init().setInitialBranch("main").setDirectory(repo).call().close();
        commit("datei.txt", "eins\nzwei\ndrei\n", "Erste Datei", AUTHOR);
        commit("datei.txt", "eins\nzwei\ndrei\nvier\n", "Vierte Zeile", AUTHOR);
        commit("andere.txt", "x\n", "Andere Datei", OTHER);
    }

    @After
    public void tearDown() {
        SystemReader.setInstance(originalReader);
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Verlauf                                                                                    */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void logListsNewestFirstWithRefs() throws Exception {
        final List<CommitInfo> log = advanced.log(repo, LogQuery.head());

        assertEquals(List.of("Andere Datei", "Vierte Zeile", "Erste Datei"),
                log.stream().map(CommitInfo::subject).collect(Collectors.toList()));
        assertEquals(List.of(new RefLabel("HEAD", RefLabel.Kind.HEAD), new RefLabel("main", RefLabel.Kind.BRANCH)),
                log.get(0).refs());
        assertEquals("Anna", log.get(2).authorName());
        assertEquals(7, log.get(0).shortId().length());
    }

    @Test
    public void logPagesWithSkipAndLimit() throws Exception {
        final LogQuery second = new LogQuery(null, false, "", "", "", 1, 1);

        final List<CommitInfo> page = advanced.log(repo, second);

        assertEquals(List.of("Vierte Zeile"), page.stream().map(CommitInfo::subject).collect(Collectors.toList()));
    }

    @Test
    public void logFiltersByAuthorTextAndPath() throws Exception {
        assertEquals(List.of("Andere Datei"), subjects(new LogQuery(null, false, "bernd", "", "", 0, 50)));
        assertEquals(List.of("Vierte Zeile"), subjects(new LogQuery(null, false, "", "VIERTE", "", 0, 50)));
        assertEquals(List.of("Vierte Zeile", "Erste Datei"), subjects(new LogQuery(null, false, "", "", "datei.txt", 0, 50)));
    }

    @Test
    public void logOfAllBranchesIncludesOtherBranchesAndTags() throws Exception {
        try (Git git = Git.open(repo)) {
            git.checkout().setCreateBranch(true).setName("feature").call();
        }
        commit("feature.txt", "f\n", "Nur im Feature", AUTHOR);
        try (Git git = Git.open(repo)) {
            git.checkout().setName("main").call();
            git.tag().setName("v1").setAnnotated(false).call();
        }

        final List<CommitInfo> all = advanced.log(repo, new LogQuery(null, true, "", "", "", 0, 50));

        assertTrue(all.stream().anyMatch(info -> info.subject().equals("Nur im Feature")));
        assertTrue(all.stream().flatMap(info -> info.refs().stream()).anyMatch(label -> label.equals(new RefLabel("v1", RefLabel.Kind.TAG))));
    }

    @Test
    public void logOfARepoWithoutCommitsIsEmpty() throws Exception {
        final File empty = folder.newFolder("leer");
        Git.init().setInitialBranch("main").setDirectory(empty).call().close();

        assertTrue(advanced.log(empty, LogQuery.head()).isEmpty());
    }

    @Test
    public void commitDetailListsChangedFilesWithLineCounts() throws Exception {
        final String id = advanced.log(repo, LogQuery.head()).get(1).id();

        final CommitDetail detail = advanced.commit(repo, id);

        assertEquals("Vierte Zeile", detail.info().subject());
        assertEquals(1, detail.files().size());
        final FileChange change = detail.files().get(0);
        assertEquals("datei.txt", change.path());
        assertEquals(FileChange.Kind.MODIFIED, change.kind());
        assertEquals(1, change.added());
        assertEquals(0, change.removed());
    }

    @Test
    public void theRootCommitShowsEverythingAsAdded() throws Exception {
        final String id = advanced.log(repo, LogQuery.head()).get(2).id();

        final CommitDetail detail = advanced.commit(repo, id);

        assertEquals(FileChange.Kind.ADDED, detail.files().get(0).kind());
        assertEquals(3, detail.files().get(0).added());
    }

    @Test
    public void anUnknownCommitIsNotFound() {
        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> advanced.commit(repo, "0000000000000000000000000000000000000000"));

        assertEquals(GitFailureKind.NOT_FOUND, failure.kind());
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Diff                                                                                       */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void commitDiffHasHunksWithNumberedLines() throws Exception {
        final String id = advanced.log(repo, LogQuery.head()).get(1).id();

        final FileDiff diff = advanced.diffCommit(repo, id, "datei.txt", false);

        assertEquals(1, diff.hunks().size());
        final List<DiffLine> lines = diff.hunks().get(0).lines();
        assertEquals(new DiffLine(DiffLine.Type.CONTEXT, 1, 1, "eins"), lines.get(0));
        final DiffLine added = lines.get(lines.size() - 1);
        assertEquals(DiffLine.Type.ADDED, added.type());
        assertEquals("vier", added.text());
        assertEquals(4, added.newNumber());
    }

    @Test
    public void ignoringWhitespaceHidesIndentationOnlyChanges() throws Exception {
        commit("datei.txt", "eins\n  zwei\ndrei\nvier\n", "Nur eingerückt", AUTHOR);
        final String id = advanced.log(repo, LogQuery.head()).get(0).id();

        assertFalse(advanced.diffCommit(repo, id, "datei.txt", false).isEmpty());
        assertTrue(advanced.diffCommit(repo, id, "datei.txt", true).isEmpty());
    }

    @Test
    public void aBinaryFileHasNoLines() throws Exception {
        final File binary = new File(repo, "bild.bin");
        Files.write(binary.toPath(), new byte[] {1, 2, 0, 3, 0, 4});
        try (Git git = Git.open(repo)) {
            git.add().addFilepattern("bild.bin").call();
            git.commit().setMessage("Binär").setAuthor(AUTHOR).setCommitter(AUTHOR).call();
        }
        final String id = advanced.log(repo, LogQuery.head()).get(0).id();

        final FileDiff diff = advanced.diffCommit(repo, id, "bild.bin", false);

        assertTrue(diff.binary());
        assertTrue(diff.hunks().isEmpty());
    }

    @Test
    public void aHugeFileGetsNoLineDiff() throws Exception {
        final String huge = "zeile\n".repeat(HistoryReader.MAX_DIFF_BYTES / 6 + 10);
        commit("riesig.txt", huge, "Riesig", AUTHOR);
        final String id = advanced.log(repo, LogQuery.head()).get(0).id();

        final FileDiff diff = advanced.diffCommit(repo, id, "riesig.txt", false);

        assertTrue(diff.tooLarge());
    }

    @Test
    public void workingTreeDiffShowsUnstagedChanges() throws Exception {
        write("datei.txt", "eins\nZWEI\ndrei\nvier\n");

        final FileDiff diff = advanced.diffWorkingTree(repo, "datei.txt", GitAdvanced.DiffBase.WORKTREE_VS_INDEX, false);

        final List<DiffLine> changed = diff.hunks().get(0).lines().stream()
                .filter(line -> line.type() != DiffLine.Type.CONTEXT).collect(Collectors.toList());
        assertEquals(List.of(DiffLine.Type.REMOVED, DiffLine.Type.ADDED), changed.stream().map(DiffLine::type).collect(Collectors.toList()));
        assertEquals("ZWEI", changed.get(1).text());
    }

    @Test
    public void stagedDiffComparesTheIndexWithHead() throws Exception {
        write("datei.txt", "eins\nzwei\ndrei\nvier\nfünf\n");
        try (Git git = Git.open(repo)) {
            git.add().addFilepattern("datei.txt").call();
        }

        final FileDiff staged = advanced.diffWorkingTree(repo, "datei.txt", GitAdvanced.DiffBase.INDEX_VS_HEAD, false);
        final FileDiff unstaged = advanced.diffWorkingTree(repo, "datei.txt", GitAdvanced.DiffBase.WORKTREE_VS_INDEX, false);

        assertFalse(staged.isEmpty());
        assertTrue(unstaged.isEmpty());
    }

    @Test
    public void anUntrackedFileIsShownAsCompletelyAdded() throws Exception {
        write("neu.txt", "a\nb\n");

        final FileDiff diff = advanced.diffWorkingTree(repo, "neu.txt", GitAdvanced.DiffBase.WORKTREE_VS_INDEX, false);

        final List<DiffLine> lines = diff.hunks().get(0).lines();
        assertEquals(2, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.type() == DiffLine.Type.ADDED));
        assertEquals(2, lines.get(1).newNumber());
    }

    @Test
    public void unifiedDiffParserNumbersLinesAcrossHunks() {
        final String text = "diff --git a/x b/x\n--- a/x\n+++ b/x\n@@ -1,2 +1,2 @@\n a\n-b\n+c\n@@ -10,1 +10,2 @@\n z\n+y\n\\ No newline at end of file\n";

        final var hunks = UnifiedDiffParser.parse(text);

        assertEquals(2, hunks.size());
        assertEquals(new DiffLine(DiffLine.Type.REMOVED, 2, 0, "b"), hunks.get(0).lines().get(1));
        assertEquals(new DiffLine(DiffLine.Type.ADDED, 0, 11, "y"), hunks.get(1).lines().get(1));
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Branches                                                                                   */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void branchesMarkTheCurrentOneFirst() throws Exception {
        advanced.createBranch(repo, "alt", null, false);

        final List<BranchInfo> branches = advanced.branches(repo);

        assertEquals(List.of("main", "alt"), branches.stream().map(BranchInfo::name).collect(Collectors.toList()));
        assertTrue(branches.get(0).current());
        assertFalse(branches.get(1).current());
    }

    @Test
    public void createAndCheckoutSwitchesTheBranch() throws Exception {
        advanced.createBranch(repo, "neu", null, true);

        assertEquals("neu", currentBranch());
    }

    @Test
    public void invalidBranchNamesAreRefused() {
        assertEquals(GitFailureKind.INVALID_NAME, kindOf(() -> advanced.createBranch(repo, "mit leerzeichen", null, false)));
        assertEquals(GitFailureKind.INVALID_NAME, kindOf(() -> advanced.createBranch(repo, "a..b", null, false)));
        assertEquals(GitFailureKind.INVALID_NAME, kindOf(() -> advanced.createBranch(repo, "", null, false)));
    }

    @Test
    public void anExistingBranchNameIsRefused() {
        assertEquals(GitFailureKind.ALREADY_EXISTS, kindOf(() -> advanced.createBranch(repo, "main", null, false)));
    }

    @Test
    public void renamingMovesTheBranch() throws Exception {
        advanced.createBranch(repo, "alt", null, false);

        advanced.renameBranch(repo, "alt", "neu");

        assertTrue(advanced.branches(repo).stream().anyMatch(branch -> branch.name().equals("neu")));
        assertFalse(advanced.branches(repo).stream().anyMatch(branch -> branch.name().equals("alt")));
    }

    @Test
    public void deletingAnUnmergedBranchNeedsForce() throws Exception {
        advanced.createBranch(repo, "feature", null, true);
        commit("feature.txt", "f\n", "Nur im Feature", AUTHOR);
        advanced.checkoutBranch(repo, "main");

        assertEquals(GitFailureKind.NOT_MERGED, kindOf(() -> advanced.deleteBranch(repo, "feature", false)));
        advanced.deleteBranch(repo, "feature", true);

        assertFalse(advanced.branches(repo).stream().anyMatch(branch -> branch.name().equals("feature")));
    }

    @Test
    public void theCurrentBranchCannotBeDeleted() {
        assertEquals(GitFailureKind.CURRENT_BRANCH, kindOf(() -> advanced.deleteBranch(repo, "main", true)));
    }

    @Test
    public void aRemoteBranchIsCheckedOutAsATrackingBranch() throws Exception {
        final File remote = folder.newFolder("remote.git");
        Git.init().setBare(true).setInitialBranch("main").setDirectory(remote).call().close();
        try (Git git = Git.open(repo)) {
            git.remoteAdd().setName("origin").setUri(new org.eclipse.jgit.transport.URIish(remote.getAbsolutePath())).call();
            git.push().setRemote("origin").add("main").call();
            git.checkout().setCreateBranch(true).setName("feature").call();
        }
        commit("f.txt", "f\n", "Feature", AUTHOR);
        try (Git git = Git.open(repo)) {
            git.push().setRemote("origin").add("feature").call();
            git.checkout().setName("main").call();
            git.branchDelete().setBranchNames("feature").setForce(true).call();
            git.fetch().setRemote("origin").call();
        }

        final String local = advanced.checkoutRemoteBranch(repo, "origin/feature");

        assertEquals("feature", local);
        assertEquals("feature", currentBranch());
        final BranchInfo info = advanced.branches(repo).stream().filter(branch -> branch.name().equals("feature")).findFirst().orElseThrow();
        assertEquals("origin/feature", info.upstream());
        assertEquals(0, info.ahead());
    }

    @Test
    public void checkingOutACommitDetachesHead() throws Exception {
        final String first = advanced.log(repo, LogQuery.head()).get(2).id();

        advanced.checkoutCommit(repo, first);

        try (Git git = Git.open(repo)) {
            assertEquals(first, git.getRepository().resolve("HEAD").getName());
            assertFalse(git.getRepository().getFullBranch().startsWith("refs/heads/"));
        }
    }

    @Test
    public void mergeFastForwardsWhenPossible() throws Exception {
        advanced.createBranch(repo, "feature", null, true);
        commit("f.txt", "f\n", "Feature", AUTHOR);
        advanced.checkoutBranch(repo, "main");

        assertEquals(GitAdvanced.MergeOutcome.FAST_FORWARDED, advanced.merge(repo, "feature"));
        assertEquals(GitAdvanced.MergeOutcome.ALREADY_UP_TO_DATE, advanced.merge(repo, "feature"));
        assertTrue(new File(repo, "f.txt").isFile());
    }

    @Test
    public void mergeCreatesAMergeCommitForDivergedBranches() throws Exception {
        advanced.createBranch(repo, "feature", null, true);
        commit("f.txt", "f\n", "Feature", AUTHOR);
        advanced.checkoutBranch(repo, "main");
        commit("m.txt", "m\n", "Main", AUTHOR);

        assertEquals(GitAdvanced.MergeOutcome.MERGED, advanced.merge(repo, "feature"));

        assertTrue(advanced.log(repo, LogQuery.head()).get(0).isMerge());
    }

    @Test
    public void mergeConflictsStayInTheRepo() throws Exception {
        advanced.createBranch(repo, "feature", null, true);
        commit("datei.txt", "feature\n", "Feature ändert", AUTHOR);
        advanced.checkoutBranch(repo, "main");
        commit("datei.txt", "main\n", "Main ändert", AUTHOR);

        final GitFailureException failure = assertThrows(GitFailureException.class, () -> advanced.merge(repo, "feature"));

        assertEquals(GitFailureKind.CONFLICT, failure.kind());
        assertEquals(List.of("datei.txt"), failure.paths());
        try (Git git = Git.open(repo)) {
            assertEquals(RepositoryState.MERGING, git.getRepository().getRepositoryState());
        }
    }

    @Test
    public void mergingAnUnknownBranchIsNotFound() {
        assertEquals(GitFailureKind.NOT_FOUND, kindOf(() -> advanced.merge(repo, "gibt-es-nicht")));
    }

    @Test
    public void rebaseReplaysLocalCommitsOntoTheOtherBranch() throws Exception {
        advanced.createBranch(repo, "feature", null, true);
        commit("f.txt", "f\n", "Feature", AUTHOR);
        advanced.checkoutBranch(repo, "main");
        commit("m.txt", "m\n", "Main", AUTHOR);
        advanced.checkoutBranch(repo, "feature");

        advanced.rebase(repo, "main");

        assertEquals(List.of("Feature", "Main"), advanced.log(repo, LogQuery.head()).stream().limit(2)
                .map(CommitInfo::subject).collect(Collectors.toList()));
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Stash                                                                                      */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void stashingWithoutChangesFails() {
        assertEquals(GitFailureKind.NOTHING_TO_COMMIT, kindOf(() -> advanced.stash(repo, "nichts", false)));
    }

    @Test
    public void stashSavesAndRestoresChanges() throws Exception {
        write("datei.txt", "geändert\n");

        advanced.stash(repo, "Mein Stand", false);

        assertEquals("eins\nzwei\ndrei\nvier\n", read("datei.txt"));
        final List<StashInfo> stashes = advanced.stashes(repo);
        assertEquals(1, stashes.size());
        assertTrue(stashes.get(0).message().contains("Mein Stand"));

        advanced.applyStash(repo, 0, true);

        assertEquals("geändert\n", read("datei.txt"));
        assertTrue(advanced.stashes(repo).isEmpty());
    }

    @Test
    public void applyKeepsTheStashAndDropRemovesIt() throws Exception {
        write("datei.txt", "geändert\n");
        advanced.stash(repo, "Stand", false);

        advanced.applyStash(repo, 0, false);
        assertEquals(1, advanced.stashes(repo).size());

        advanced.dropStash(repo, 0);
        assertTrue(advanced.stashes(repo).isEmpty());
    }

    @Test
    public void stashCanIncludeUntrackedFiles() throws Exception {
        write("neu.txt", "neu\n");

        advanced.stash(repo, "Mit Neuem", true);

        assertFalse(new File(repo, "neu.txt").exists());
        advanced.applyStash(repo, 0, true);
        assertTrue(new File(repo, "neu.txt").exists());
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Tags                                                                                       */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void lightweightAndAnnotatedTagsAreListed() throws Exception {
        advanced.createTag(repo, "leicht", null, null, TAGGER);
        advanced.createTag(repo, "annotiert", null, "Version 1", TAGGER);

        final List<TagInfo> tags = advanced.tags(repo);

        assertEquals(List.of("annotiert", "leicht"), tags.stream().map(TagInfo::name).collect(Collectors.toList()));
        assertTrue(tags.get(0).annotated());
        assertEquals("Version 1", tags.get(0).message());
        assertFalse(tags.get(1).annotated());
        assertEquals(advanced.log(repo, LogQuery.head()).get(0).id(), tags.get(0).targetId());
    }

    @Test
    public void aTagCanPointAtAnOlderCommit() throws Exception {
        final String first = advanced.log(repo, LogQuery.head()).get(2).id();

        advanced.createTag(repo, "alt", first, null, TAGGER);

        assertEquals(first, advanced.tags(repo).get(0).targetId());
    }

    @Test
    public void tagNamesAreValidated() throws Exception {
        advanced.createTag(repo, "v1", null, null, TAGGER);

        assertEquals(GitFailureKind.INVALID_NAME, kindOf(() -> advanced.createTag(repo, "mit leerzeichen", null, null, TAGGER)));
        assertEquals(GitFailureKind.ALREADY_EXISTS, kindOf(() -> advanced.createTag(repo, "v1", null, null, TAGGER)));
    }

    @Test
    public void deletingATagRemovesIt() throws Exception {
        advanced.createTag(repo, "v1", null, null, TAGGER);

        advanced.deleteTag(repo, "v1");

        assertTrue(advanced.tags(repo).isEmpty());
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Remotes                                                                                    */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void remotesCanBeAddedListedAndChanged() throws Exception {
        advanced.addRemote(repo, "origin", "https://github.com/max/alpha.git");
        advanced.addRemote(repo, "zweit", "https://gitlab.com/max/alpha.git");

        assertEquals(List.of(new RemoteInfo("origin", "https://github.com/max/alpha.git"),
                new RemoteInfo("zweit", "https://gitlab.com/max/alpha.git")), advanced.remotes(repo));

        advanced.setRemoteUrl(repo, "zweit", "git@gitlab.com:max/alpha.git");
        assertEquals("git@gitlab.com:max/alpha.git", advanced.remotes(repo).get(1).url());

        advanced.removeRemote(repo, "zweit");
        assertEquals(1, advanced.remotes(repo).size());
    }

    @Test
    public void credentialsInARemoteAddressAreStripped() throws Exception {
        advanced.addRemote(repo, "origin", "https://max:geheim@github.com/max/alpha.git");

        final String url = advanced.remotes(repo).get(0).url();

        assertEquals("https://github.com/max/alpha.git", url);
        assertFalse(url.contains("geheim"));
    }

    @Test
    public void invalidOrDuplicateRemoteNamesAreRefused() throws Exception {
        advanced.addRemote(repo, "origin", "https://github.com/max/alpha.git");

        assertEquals(GitFailureKind.ALREADY_EXISTS, kindOf(() -> advanced.addRemote(repo, "origin", "https://x.example/a/b.git")));
        assertEquals(GitFailureKind.INVALID_NAME, kindOf(() -> advanced.addRemote(repo, "mit leerzeichen", "https://x.example/a/b.git")));
        assertEquals(GitFailureKind.INVALID_NAME, kindOf(() -> advanced.addRemote(repo, "neu", "")));
        assertEquals(GitFailureKind.NOT_FOUND, kindOf(() -> advanced.setRemoteUrl(repo, "gibt-es-nicht", "https://x.example/a/b.git")));
    }

    @Test
    public void renamingARemoteMovesItsTrackingRefsAndBranchConfig() throws Exception {
        final File bare = folder.newFolder("bare.git");
        Git.init().setBare(true).setInitialBranch("main").setDirectory(bare).call().close();
        try (Git git = Git.open(repo)) {
            git.remoteAdd().setName("origin").setUri(new org.eclipse.jgit.transport.URIish(bare.getAbsolutePath())).call();
            git.push().setRemote("origin").add("main").call();
            git.fetch().setRemote("origin").call();
            final var config = git.getRepository().getConfig();
            config.setString("branch", "main", "remote", "origin");
            config.setString("branch", "main", "merge", "refs/heads/main");
            config.save();
        }

        advanced.renameRemote(repo, "origin", "upstream");

        assertEquals(List.of("upstream"), advanced.remotes(repo).stream().map(RemoteInfo::name).collect(Collectors.toList()));
        try (Git git = Git.open(repo)) {
            assertEquals("upstream", git.getRepository().getConfig().getString("branch", "main", "remote"));
            assertTrue(git.getRepository().findRef("refs/remotes/upstream/main") != null);
            assertTrue(git.getRepository().findRef("refs/remotes/origin/main") == null);
        }
    }

    @Test
    public void removingARemoteDeletesItsTrackingRefs() throws Exception {
        final File bare = folder.newFolder("bare.git");
        Git.init().setBare(true).setInitialBranch("main").setDirectory(bare).call().close();
        try (Git git = Git.open(repo)) {
            git.remoteAdd().setName("origin").setUri(new org.eclipse.jgit.transport.URIish(bare.getAbsolutePath())).call();
            git.push().setRemote("origin").add("main").call();
            git.fetch().setRemote("origin").call();
        }

        advanced.removeRemote(repo, "origin");

        try (Git git = Git.open(repo)) {
            assertTrue(git.getRepository().findRef("refs/remotes/origin/main") == null);
        }
    }

    /* ------------------------------------------------------------------------------------------ */

    private interface Action {

        void run() throws Exception;
    }

    private static GitFailureKind kindOf(
            final Action action
    ) {
        return assertThrows(GitFailureException.class, action::run).kind();
    }

    private List<String> subjects(
            final LogQuery query
    ) throws Exception {
        return advanced.log(repo, query).stream().map(CommitInfo::subject).collect(Collectors.toList());
    }

    private String currentBranch() throws Exception {
        try (Git git = Git.open(repo)) {
            return git.getRepository().getBranch();
        }
    }

    private void commit(
            final String path,
            final String content,
            final String message,
            final PersonIdent author
    ) throws Exception {
        write(path, content);
        try (Git git = Git.open(repo)) {
            git.add().addFilepattern(path).call();
            git.commit().setMessage(message).setAuthor(author).setCommitter(author).call();
        }
    }

    private void write(
            final String path,
            final String content
    ) throws Exception {
        final File file = new File(repo, path);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private String read(
            final String path
    ) throws Exception {
        return new String(Files.readAllBytes(new File(repo, path).toPath()), StandardCharsets.UTF_8);
    }
}
