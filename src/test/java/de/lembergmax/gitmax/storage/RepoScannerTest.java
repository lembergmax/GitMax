package de.lembergmax.gitmax.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.LocalRepo;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class RepoScannerTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private File root;

    @Before
    public void setUp() throws IOException {
        root = folder.newFolder("projekte");
    }

    @Test
    public void findsReposAtSeveralDepthsAndSortsByPath() throws Exception {
        repo("zebra");
        repo("Alpha");
        repo("gruppe/beta");
        folder("kein-repo");

        final List<String> found = names(RepoScanner.scan(List.of(root), () -> false));

        assertEquals(List.of("Alpha", "gruppe/beta", "zebra"), found);
    }

    @Test
    public void doesNotLookInsideARepo() throws Exception {
        repo("aussen");
        repo("aussen/vendor/innen");

        assertEquals(List.of("aussen"), names(RepoScanner.scan(List.of(root), () -> false)));
    }

    @Test
    public void respectsTheMaximumDepth() throws Exception {
        repo("a/b/c");
        repo("a/b/c/d/zu-tief");
        repo("x/y/z/w");

        assertEquals(List.of("a/b/c"), names(RepoScanner.scan(List.of(root), () -> false)));
    }

    @Test
    public void skipsHiddenAndBulkyFolders() throws Exception {
        repo(".versteckt/repo");
        repo("node_modules/paket");
        repo("build/ausgabe");
        repo("Android/data");
        repo("sichtbar");

        assertEquals(List.of("sichtbar"), names(RepoScanner.scan(List.of(root), () -> false)));
    }

    @Test
    public void aRepoThatOnlyCarriesABulkyFolderNameIsStillFound() throws Exception {
        repo("android");
        repo("build");
        repo("gruppe/node_modules");
        folder("Android/data");

        assertEquals(List.of("android", "build", "gruppe/node_modules"), names(RepoScanner.scan(List.of(root), () -> false)));
    }

    @Test
    public void recognizesAGitFileAsUsedBySubmodulesAndWorktrees() throws Exception {
        final File submodule = folder("sub");
        assertTrue(new File(submodule, ".git").createNewFile());

        assertEquals(List.of("sub"), names(RepoScanner.scan(List.of(root), () -> false)));
    }

    @Test
    public void aRootThatIsItselfARepoIsFound() throws Exception {
        assertTrue(new File(root, ".git").mkdir());

        final List<LocalRepo> found = RepoScanner.scan(List.of(root), () -> false);

        assertEquals(List.of("projekte"), names(found));
        assertEquals(root, found.get(0).directory());
    }

    @Test
    public void missingRootsAreIgnored() throws Exception {
        repo("a");

        final List<LocalRepo> found = RepoScanner.scan(List.of(new File(root, "gibt-es-nicht"), root), () -> false);

        assertEquals(List.of("a"), names(found));
    }

    @Test
    public void severalRootsAreMerged() throws Exception {
        final File second = folder.newFolder("zweiter");
        repo("eins");
        assertTrue(new File(second, "zwei/.git").mkdirs());

        final List<LocalRepo> found = RepoScanner.scan(List.of(root, second), () -> false);

        assertEquals(List.of("eins", "zwei"), names(found));
        assertEquals(second, found.get(1).root());
    }

    @Test
    public void aRepoUnderNestedRootsIsListedOnce() throws Exception {
        repo("gruppe/projekt");
        final File nested = new File(root, "gruppe");

        final List<LocalRepo> found = RepoScanner.scan(List.of(root, nested), () -> false);

        assertEquals(1, found.size());
        assertEquals("erster Fund bleibt: der äußere Ordner", root, found.get(0).root());
    }

    @Test
    public void cancellationReturnsWhatWasFoundSoFar() throws Exception {
        repo("a");
        repo("b");
        final AtomicInteger asked = new AtomicInteger();

        final List<LocalRepo> found = RepoScanner.scan(List.of(root), () -> asked.incrementAndGet() > 1);

        assertTrue(found.size() < 2);
    }

    private File repo(
            final String relativePath
    ) {
        final File directory = new File(root, relativePath);
        assertTrue(new File(directory, ".git").mkdirs());
        return directory;
    }

    private File folder(
            final String relativePath
    ) {
        final File directory = new File(root, relativePath);
        assertTrue(directory.mkdirs());
        return directory;
    }

    private static List<String> names(
            final List<LocalRepo> repos
    ) {
        return repos.stream().map(LocalRepo::relativePath).toList();
    }
}
