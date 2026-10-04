package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.LogQuery;
import de.lembergmax.gitmax.domain.model.RepoStats;
import de.lembergmax.gitmax.domain.model.SubmoduleInfo;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.Optional;

/** Prüft Wartung (Aufräumen, Git-Daten verdichten), Ausschlüsse, Submodule und die Identität bei Merges. */
public final class JgitMaintenanceTest {

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
        repo.commit("datei.txt", "eins\n", "Erste Datei", TestRepo.ANNA);
        repo.commit("datei.txt", "eins\nzwei\n", "Zweite Zeile", TestRepo.ANNA);
    }

    @After
    public void tearDown() {
        SystemReader.setInstance(originalReader);
    }

    /* ------------------------------------------------------------------------------------------ */
    /* clean                                                                                       */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void cleanPreviewListsUntrackedFilesAndFoldersButNotIgnoredOnes() throws Exception {
        repo.commit(".gitignore", "*.log\n", "Ignorieren", TestRepo.ANNA);
        repo.write("neu.txt", "n\n");
        repo.write("ordner/innen.txt", "i\n");
        repo.write("debug.log", "ignoriert\n");

        final List<String> preview = advanced.cleanPreview(repo.directory());

        assertEquals(List.of("neu.txt", "ordner/"), preview);
    }

    @Test
    public void cleanRemovesOnlyTheChosenPaths() throws Exception {
        repo.write("a.txt", "a\n");
        repo.write("b.txt", "b\n");
        repo.write("ordner/c.txt", "c\n");

        advanced.clean(repo.directory(), List.of("a.txt", "ordner/"));

        assertFalse(repo.exists("a.txt"));
        assertFalse(repo.exists("ordner/c.txt"));
        assertTrue(repo.exists("b.txt"));
        assertTrue(repo.exists("datei.txt"));
    }

    @Test
    public void cleanWithoutPathsChangesNothing() throws Exception {
        repo.write("a.txt", "a\n");

        advanced.clean(repo.directory(), List.of());

        assertTrue(repo.exists("a.txt"));
    }

    @Test
    public void cleanNeverTouchesTrackedFiles() throws Exception {
        repo.write("datei.txt", "lokal verändert\n");

        advanced.clean(repo.directory(), List.of("datei.txt"));

        assertEquals("lokal verändert\n", repo.read("datei.txt"));
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Statistik und gc                                                                            */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void statsCountLooseObjectsOfAFreshRepo() throws Exception {
        final RepoStats stats = advanced.stats(repo.directory());

        assertTrue(stats.looseObjects() > 0);
        assertEquals(0, stats.packs());
        assertTrue(stats.totalBytes() > 0);
    }

    @Test
    public void collectingGarbagePacksTheObjectsAndKeepsTheHistory() throws Exception {
        final String head = repo.head();

        advanced.collectGarbage(repo.directory());

        final RepoStats stats = advanced.stats(repo.directory());
        assertEquals(0, stats.looseObjects());
        assertTrue(stats.packs() >= 1);
        assertEquals(head, repo.head());
        assertEquals(2, advanced.log(repo.directory(), LogQuery.head()).size());
        assertEquals("eins\nzwei\n", repo.read("datei.txt"));
        try (Git git = repo.open()) {
            assertTrue(git.status().call().isClean());
        }
    }

    @Test
    public void collectingGarbageKeepsBranchesAndTagsReachable() throws Exception {
        repo.createBranch("feature");
        repo.commit("f.txt", "f\n", "Feature", TestRepo.ANNA);
        try (Git git = repo.open()) {
            git.tag().setName("v1").setAnnotated(false).call();
        }
        repo.checkout("main");

        advanced.collectGarbage(repo.directory());

        assertEquals(List.of("feature", "main"), advanced.branches(repo.directory()).stream()
                .map(branch -> branch.name()).sorted().toList());
        assertEquals(1, advanced.tags(repo.directory()).size());
    }

    @Test
    public void collectingGarbageTwiceIsHarmless() throws Exception {
        advanced.collectGarbage(repo.directory());
        advanced.collectGarbage(repo.directory());

        assertEquals(2, advanced.log(repo.directory(), LogQuery.head()).size());
    }

    /* ------------------------------------------------------------------------------------------ */
    /* info/exclude                                                                                */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void excludeStartsWithGitsOwnTemplateOrEmpty() throws Exception {
        final String text = advanced.readExclude(repo.directory());

        assertFalse(text.contains("meine-datei"));
    }

    @Test
    public void writtenExcludesHideFilesFromTheUntrackedList() throws Exception {
        repo.write("geheim.txt", "g\n");
        repo.write("sichtbar.txt", "s\n");

        advanced.writeExclude(repo.directory(), "geheim.txt\n");

        assertEquals("geheim.txt\n", advanced.readExclude(repo.directory()));
        try (Git git = repo.open()) {
            assertEquals(java.util.Set.of("sichtbar.txt"), git.status().call().getUntracked());
        }
    }

    @Test
    public void excludeWorksWhenTheInfoFolderIsMissing() throws Exception {
        final File info = new File(repo.directory(), ".git/info");
        assertFalse("Ein frisches JGit-Repo hat keinen info-Ordner", info.exists());

        advanced.writeExclude(repo.directory(), "x\n");

        assertEquals("x\n", advanced.readExclude(repo.directory()));
    }

    /* ------------------------------------------------------------------------------------------ */
    /* fetch.prune                                                                                 */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void pruneOnFetchDefaultsToOnAndCanBeSwitched() throws Exception {
        assertTrue(advanced.pruneOnFetch(repo.directory()));

        advanced.setPruneOnFetch(repo.directory(), false);
        assertFalse(advanced.pruneOnFetch(repo.directory()));

        advanced.setPruneOnFetch(repo.directory(), true);
        assertTrue(advanced.pruneOnFetch(repo.directory()));
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Submodule                                                                                   */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void aRepoWithoutSubmodulesListsNone() throws Exception {
        assertTrue(advanced.submodules(repo.directory()).isEmpty());
    }

    @Test
    public void anInitializedSubmoduleIsReported() throws Exception {
        final File library = libraryRepo();
        try (Git git = repo.open()) {
            git.submoduleAdd().setPath("lib").setURI(library.toURI().toString()).call().close();
            git.commit().setMessage("Submodul").setAuthor(TestRepo.ANNA).setCommitter(TestRepo.ANNA).call();
        }

        final List<SubmoduleInfo> modules = advanced.submodules(repo.directory());

        assertEquals(1, modules.size());
        assertEquals("lib", modules.get(0).path());
        assertTrue(modules.get(0).url().contains("library"));
        assertTrue(modules.get(0).initialized());
        assertTrue(modules.get(0).upToDate());
    }

    @Test
    public void aFreshCloneListsTheSubmoduleAsNotInitialized() throws Exception {
        final File library = libraryRepo();
        try (Git git = repo.open()) {
            git.submoduleAdd().setPath("lib").setURI(library.toURI().toString()).call().close();
            git.commit().setMessage("Submodul").setAuthor(TestRepo.ANNA).setCommitter(TestRepo.ANNA).call();
        }
        final File clone = folder.newFolder("klon");
        Git.cloneRepository().setURI(repo.directory().toURI().toString()).setDirectory(clone).call().close();

        final List<SubmoduleInfo> modules = advanced.submodules(clone);

        assertEquals(1, modules.size());
        assertFalse(modules.get(0).initialized());
        assertFalse(modules.get(0).upToDate());
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Identität bei Vorgängen, die Commits erzeugen                                               */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void mergeCommitsUseTheResolvedIdentityNotTheAndroidUser() throws Exception {
        final CommitIdentity max = new CommitIdentity("Max Lemberg", "max@example.invalid");
        final JgitAdvanced withIdentity = new JgitAdvanced(ignored -> Optional.of(max));
        repo.createBranch("feature");
        repo.commit("f.txt", "f\n", "Feature", TestRepo.ANNA);
        repo.checkout("main");
        repo.commit("m.txt", "m\n", "Main", TestRepo.ANNA);

        withIdentity.merge(repo.directory(), "feature");

        final var head = withIdentity.log(repo.directory(), LogQuery.head()).get(0);
        assertTrue(head.isMerge());
        assertEquals("Max Lemberg", head.authorName());
        assertEquals("max@example.invalid", head.authorEmail());
    }

    @Test
    public void theResolvedIdentityReplacesAnOlderRepoConfig() throws Exception {
        try (Git git = repo.open()) {
            final var config = git.getRepository().getConfig();
            config.setString("user", null, "name", "Vorhanden");
            config.setString("user", null, "email", "vorhanden@example.invalid");
            config.save();
        }
        final JgitAdvanced withIdentity = new JgitAdvanced(
                ignored -> Optional.of(new CommitIdentity("Anderer", "anderer@example.invalid")));
        repo.createBranch("feature");
        repo.commit("f.txt", "f\n", "Feature", TestRepo.ANNA);
        repo.checkout("main");
        repo.commit("m.txt", "m\n", "Main", TestRepo.ANNA);

        withIdentity.merge(repo.directory(), "feature");

        assertEquals("Anderer", withIdentity.log(repo.directory(), LogQuery.head()).get(0).authorName());
    }

    @Test
    public void toolsOnAFolderThatIsNoRepoReportNotARepo() throws Exception {
        final File empty = folder.newFolder("leer");

        final GitFailureException failure = assertThrows(GitFailureException.class, () -> advanced.stats(empty));

        assertEquals(GitFailureKind.NOT_A_REPO, failure.kind());
    }

    /* ------------------------------------------------------------------------------------------ */

    private File libraryRepo() throws Exception {
        final TestRepo library = TestRepo.init(folder.newFolder("library"));
        library.commit("lib.txt", "lib\n", "Bibliothek", TestRepo.BERND);
        return library.directory();
    }
}
