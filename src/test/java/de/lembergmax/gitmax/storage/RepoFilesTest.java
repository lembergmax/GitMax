package de.lembergmax.gitmax.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

public final class RepoFilesTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private File root;
    private RepoFiles files;

    @Before
    public void setUp() throws Exception {
        root = folder.newFolder("repo");
        assertTrue(new File(root, ".git").mkdir());
        write("README.md", "x");
        write("src/Main.java", "class Main {}");
        write("src/util/Helper.java", "class Helper {}");
        write("zebra.txt", "z");
        assertTrue(new File(root, "Docs").mkdir());
        files = new RepoFiles(root);
    }

    @Test
    public void listsFoldersFirstThenFilesAndHidesGit() throws Exception {
        final List<String> names = files.list("").stream().map(RepoFiles.Entry::name).collect(Collectors.toList());

        assertEquals(List.of("Docs", "src", "README.md", "zebra.txt"), names);
    }

    @Test
    public void listsASubfolderWithRelativePaths() throws Exception {
        final List<RepoFiles.Entry> entries = files.list("src");

        assertEquals("src/util", entries.get(0).path());
        assertTrue(entries.get(0).directory());
        assertEquals("src/Main.java", entries.get(1).path());
        assertEquals(13, entries.get(1).size());
    }

    @Test
    public void pathsOutsideTheRepoAreRefused() {
        assertEquals(RepoFiles.Reason.PROTECTED, reasonOf(() -> files.resolve("../fremd")));
        assertEquals(RepoFiles.Reason.PROTECTED, reasonOf(() -> files.resolve("src/../../fremd")));
        assertEquals(RepoFiles.Reason.PROTECTED, reasonOf(() -> files.list("..")));
    }

    @Test
    public void theGitDirectoryIsOffLimits() {
        assertEquals(RepoFiles.Reason.PROTECTED, reasonOf(() -> files.resolve(".git/config")));
        assertEquals(RepoFiles.Reason.PROTECTED, reasonOf(() -> files.delete(".git")));
        assertEquals(RepoFiles.Reason.INVALID_NAME, reasonOf(() -> files.createFolder("", ".git")));
        assertEquals(RepoFiles.Reason.INVALID_NAME, reasonOf(() -> files.rename("src", ".git/x")));
    }

    @Test
    public void createsFilesAndFolders() throws Exception {
        assertEquals("src/Neu.java", files.createFile("src", " Neu.java "));
        assertEquals("Ideen", files.createFolder("", "Ideen"));

        assertTrue(new File(root, "src/Neu.java").isFile());
        assertTrue(new File(root, "Ideen").isDirectory());
    }

    @Test
    public void refusesInvalidAndDuplicateNames() {
        assertEquals(RepoFiles.Reason.INVALID_NAME, reasonOf(() -> files.createFile("", "a:b.txt")));
        assertEquals(RepoFiles.Reason.INVALID_NAME, reasonOf(() -> files.createFile("", "  ")));
        assertEquals(RepoFiles.Reason.EXISTS, reasonOf(() -> files.createFile("", "README.md")));
        assertEquals(RepoFiles.Reason.EXISTS, reasonOf(() -> files.createFolder("", "src")));
    }

    @Test
    public void renamesWithinTheSameFolder() throws Exception {
        final String renamed = files.rename("src/Main.java", "App.java");

        assertEquals("src/App.java", renamed);
        assertFalse(new File(root, "src/Main.java").exists());
        assertTrue(new File(root, "src/App.java").isFile());
    }

    @Test
    public void renamingOntoAnExistingNameFails() {
        assertEquals(RepoFiles.Reason.EXISTS, reasonOf(() -> files.rename("README.md", "zebra.txt")));
    }

    @Test
    public void deletesFilesAndWholeFolders() throws Exception {
        files.delete("zebra.txt");
        files.delete("src");

        assertFalse(new File(root, "zebra.txt").exists());
        assertFalse(new File(root, "src").exists());
    }

    @Test
    public void theRepoItselfIsNotDeletable() {
        assertEquals(RepoFiles.Reason.PROTECTED, reasonOf(() -> files.delete("")));
        assertTrue(root.exists());
    }

    @Test
    public void deletingSomethingMissingReportsNotFound() {
        assertEquals(RepoFiles.Reason.NOT_FOUND, reasonOf(() -> files.delete("gibt-es-nicht.txt")));
    }

    private interface Action {

        void run() throws Exception;
    }

    private static RepoFiles.Reason reasonOf(
            final Action action
    ) {
        final RepoFiles.FilesException failure = assertThrows(RepoFiles.FilesException.class, action::run);
        return failure.reason();
    }

    private void write(
            final String path,
            final String content
    ) throws Exception {
        final File file = new File(root, path);
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
