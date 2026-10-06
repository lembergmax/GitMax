package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

/** Prüft den Klon in Teilschritten gegen ein lokales Remote, das Filter und einzelne Objekte erlaubt. */
public final class BatchedCloneTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.invalid");
    private static final int FILE_COUNT = 25;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private SystemReader originalReader;
    private File source;

    @Before
    public void setUp() throws Exception {
        originalReader = SystemReader.getInstance();
        SystemReader.setInstance(new AppSystemReader(originalReader, folder.newFolder("git-home")));
        source = folder.newFolder("source");
    }

    @After
    public void tearDown() {
        SystemReader.setInstance(originalReader);
    }

    @Test
    public void cloneInSeveralStepsGivesTheSameRepoAsOneStep() throws Exception {
        seedSource(true);
        final File work = new File(folder.getRoot(), "work");

        try (Git git = run(work, null, false, 4).orElseThrow()) {
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call();

            assertEquals("main", git.getRepository().getBranch());
            for (int i = 0; i < FILE_COUNT; i++) {
                assertEquals("inhalt " + i, read(new File(work, "datei" + i + ".txt")));
            }
            assertTrue("Mehrere Pakete: ein Paket je Schritt", packCount(work) > 1);
            assertTrue(git.status().call().isClean());
            assertEquals("origin", git.getRepository().getConfig().getString("branch", "main", "remote"));
            assertEquals("refs/heads/main", git.getRepository().getConfig().getString("branch", "main", "merge"));
            assertTrue(git.getRepository().getObjectDatabase().has(git.getRepository().resolve("origin/main")));
        }
    }

    @Test
    public void aServerWithoutFilterSupportMakesTheCallerFallBack() throws Exception {
        seedSource(false);
        final File work = new File(folder.getRoot(), "work");

        final Optional<Git> result = run(work, null, false, 4);

        assertFalse(result.isPresent());
    }

    @Test
    public void aShallowCloneKeepsOnlyTheLatestCommit() throws Exception {
        seedSource(true);
        final File work = new File(folder.getRoot(), "work");

        try (Git git = run(work, null, true, 4).orElseThrow()) {
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call();

            int commits = 0;
            for (final var ignored : git.log().call()) {
                commits++;
            }
            assertEquals(1, commits);
            assertEquals("inhalt 3", read(new File(work, "datei3.txt")));
        }
    }

    @Test
    public void aNamedBranchIsCheckedOut() throws Exception {
        seedSource(true);
        try (Git git = Git.open(source)) {
            git.checkout().setCreateBranch(true).setName("zweig").call();
            write(new File(source, "zweig.txt"), "nur im zweig");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("zweig").setAuthor(IDENT).setCommitter(IDENT).call();
            git.checkout().setName("main").call();
        }
        final File work = new File(folder.getRoot(), "work");

        try (Git git = run(work, "zweig", false, 4).orElseThrow()) {
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call();

            assertEquals("zweig", git.getRepository().getBranch());
            assertEquals("nur im zweig", read(new File(work, "zweig.txt")));
        }
    }

    private Optional<Git> run(
            final File work,
            final String branch,
            final boolean shallow,
            final int firstBatch
    ) throws Exception {
        return new BatchedClone(source.toURI().toString(), work, branch, shallow, null, null, 30,
                new ProgressAdapter(GitProgress.NONE), firstBatch, 1L).run();
    }

    private void seedSource(
            final boolean allowFilter
    ) throws Exception {
        try (Git git = Git.init().setInitialBranch("main").setDirectory(source).call()) {
            final StoredConfig config = git.getRepository().getConfig();
            config.setBoolean("uploadpack", null, "allowfilter", allowFilter);
            config.setBoolean("uploadpack", null, "allowanysha1inwant", allowFilter);
            config.save();
            write(new File(source, "start.txt"), "erster stand");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("eins").setAuthor(IDENT).setCommitter(IDENT).call();
            for (int i = 0; i < FILE_COUNT; i++) {
                write(new File(source, "datei" + i + ".txt"), "inhalt " + i);
            }
            git.add().addFilepattern(".").call();
            git.commit().setMessage("zwei").setAuthor(IDENT).setCommitter(IDENT).call();
        }
    }

    private static int packCount(
            final File work
    ) {
        final String[] packs = new File(work, ".git/objects/pack").list((dir, name) -> name.endsWith(".pack"));
        return packs == null ? 0 : packs.length;
    }

    private static void write(
            final File file,
            final String content
    ) throws Exception {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(
            final File file
    ) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
