package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Environment;

import androidx.test.platform.app.InstrumentationRegistry;

import de.lembergmax.gitmax.domain.CloneJournal;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.CloneRequest;
import de.lembergmax.gitmax.testsupport.GitFixtures;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Klont ein Repo mit vielen kleinen und einigen großen Dateien (über der Stromgrenze von JGit) im App-Speicher
 * und auf dem Telefonspeicher. Beim Auschecken großer Dateien trat auf Geräten „Inflater has been closed“ auf.
 */
public final class JgitLargeFilesDeviceTest {

    private static final int LARGE_BYTES = 12 * 1024 * 1024;
    private static final int LARGE_COUNT = 5;
    private static final int SMALL_COUNT = 150;

    private File base;
    private File remote;
    private RemoteUrl url;

    @Before
    public void setUp() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        JgitEnvironment.install(context);
        base = new File(context.getCacheDir(), "large-" + UUID.randomUUID());
        assertTrue(base.mkdirs());
        remote = new File(base, "source");
        GitFixtures.seedWithBinaryFiles(remote, SMALL_COUNT, LARGE_COUNT, LARGE_BYTES);
        url = RemoteUrl.parse("https://example.invalid/max/alpha.git").orElseThrow();
    }

    @After
    public void tearDown() throws Exception {
        try (Stream<java.nio.file.Path> walk = Files.walk(base.toPath())) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    public void cloneChecksOutLargeFilesInAppStorage() throws Exception {
        final File work = new File(base, "work");

        newEngine().cloneRepository(new CloneRequest(url, work, null, false, false), GitProgress.NONE);

        assertEquals(LARGE_BYTES, new File(work, "datei0.bin").length());
        assertEquals(LARGE_BYTES, new File(work, "datei" + (LARGE_COUNT - 1) + ".bin").length());
        assertEquals(2048, new File(work, "datei" + LARGE_COUNT + ".bin").length());
    }

    @Test
    public void cloneChecksOutLargeFilesOnPhoneStorage() throws Exception {
        final File work = new File(Environment.getExternalStorageDirectory(), "GitMaxTest/large-" + UUID.randomUUID());
        try {
            newEngine().cloneRepository(new CloneRequest(url, work, null, false, false), GitProgress.NONE);

            assertEquals(LARGE_BYTES, new File(work, "datei0.bin").length());
        } finally {
            if (work.exists()) {
                try (Stream<java.nio.file.Path> walk = Files.walk(work.toPath())) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
                }
            }
        }
    }

    private JgitEngine newEngine() {
        return new JgitEngine(GitCredentials.NONE, JgitEngine::isSharedStoragePath, CloneJournal.NONE,
                IdentitySource.NONE, ignored -> remote.getAbsolutePath());
    }
}
