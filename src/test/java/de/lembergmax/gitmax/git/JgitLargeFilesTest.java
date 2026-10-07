package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;

import de.lembergmax.gitmax.domain.CloneJournal;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.CloneRequest;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Random;

/** Klont ein Repo mit vielen und mit großen Dateien, die JGit beim Auschecken als Datenstrom liest. */
public final class JgitLargeFilesTest {

    private static final int LARGE_FILE_BYTES = 3 * 1024 * 1024;
    private static final int FILE_COUNT = 150;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private SystemReader originalReader;

    @Before
    public void setUp() throws Exception {
        originalReader = SystemReader.getInstance();
        SystemReader.setInstance(new AppSystemReader(originalReader, folder.newFolder("git-home")));
        final WindowCacheConfig config = new WindowCacheConfig();
        config.setStreamFileThreshold(1024 * 1024);
        config.install();
    }

    @After
    public void tearDown() {
        SystemReader.setInstance(originalReader);
        // Die Fenster-Einstellung gilt für die ganze JVM; ohne das liefen alle folgenden Testklassen mit dem kleinen Schwellwert.
        new WindowCacheConfig().install();
    }

    @Test
    public void cloneChecksOutManyLargeFilesFromAPack() throws Exception {
        final File remote = new File(folder.getRoot(), "remote.git");
        Git.init().setBare(true).setInitialBranch("main").setDirectory(remote).call().close();
        final File seed = folder.newFolder("seed");
        final Random random = new Random(7);
        try (Git git = Git.cloneRepository().setURI(remote.toURI().toString()).setDirectory(seed).call()) {
            for (int i = 0; i < FILE_COUNT; i++) {
                final byte[] content = new byte[i % 3 == 0 ? LARGE_FILE_BYTES : 2048];
                random.nextBytes(content);
                Files.write(new File(seed, "datei" + i + ".bin").toPath(), content);
            }
            git.add().addFilepattern(".").call();
            git.commit().setMessage("viele Dateien").setAuthor(new PersonIdent("T", "t@example.invalid")).call();
            git.push().setRemote("origin").add("HEAD:refs/heads/main").call();
        }
        final RemoteUrl url = RemoteUrl.parse("https://example.invalid/max/alpha.git").orElseThrow();
        final JgitEngine engine = new JgitEngine(GitCredentials.NONE, file -> false, new CloneJournal() {
            @Override
            public void begin(final File target) {
            }

            @Override
            public void end(final File target) {
            }
        }, IdentitySource.NONE, ignored -> remote.toURI().toString());
        final File work = new File(folder.getRoot(), "work");

        engine.cloneRepository(new CloneRequest(url, work, null, false, false), GitProgress.NONE);

        assertEquals(LARGE_FILE_BYTES, new File(work, "datei0.bin").length());
        assertEquals(2048, new File(work, "datei1.bin").length());
    }
}
