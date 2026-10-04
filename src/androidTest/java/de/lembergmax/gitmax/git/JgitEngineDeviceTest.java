package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

import androidx.test.platform.app.InstrumentationRegistry;

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
import de.lembergmax.gitmax.testsupport.GitFixtures;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Prüft die Engine auf dem echten Telefonspeicher (FUSE): Klon mit Symlink, Umlauten und Leerzeichen,
 * Commit, Push und Update über zwei Klone, und der Umgang mit Dateinamen, die dort verboten sind.
 * Nutzt nur öffentliche App-Klassen, damit derselbe Test gegen den R8-Build ({@code minified}) läuft.
 */
public final class JgitEngineDeviceTest {

    private static final CommitIdentity IDENTITY = new CommitIdentity("Max", "max@example.invalid");

    private File base;
    private File remote;
    private File scratch;
    private RemoteUrl url;
    private JgitEngine engine;

    @BeforeClass
    public static void grantAllFilesAccess() throws Exception {
        final String packageName = InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
        shell("appops set --uid " + packageName + " MANAGE_EXTERNAL_STORAGE allow");
        assertTrue("Zugriff auf alle Dateien nicht erteilt", Environment.isExternalStorageManager());
    }

    @Before
    public void setUp() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        JgitEnvironment.install(context);
        base = new File(Environment.getExternalStorageDirectory(), "GitMaxTest/" + UUID.randomUUID());
        assertTrue("Testordner nicht anlegbar: " + base, base.mkdirs());
        remote = GitFixtures.createBareRemote(new File(base, "remote.git"));
        scratch = new File(context.getCacheDir(), "fixture-" + UUID.randomUUID());
        url = RemoteUrl.parse("https://example.invalid/max/alpha.git").orElseThrow();
        engine = new JgitEngine(GitCredentials.NONE, JgitEngine::isSharedStoragePath, CloneJournal.NONE,
                IdentitySource.NONE, ignored -> remote.getAbsolutePath());
    }

    @After
    public void tearDown() throws IOException {
        for (final File root : new File[] {base, scratch}) {
            if (root == null || !root.exists()) {
                continue;
            }
            try (Stream<java.nio.file.Path> walk = Files.walk(root.toPath())) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    public void cloneOnPhoneStorageChecksOutEverythingAndStaysClean() throws Exception {
        GitFixtures.seedTypicalProject(remote, scratch);
        final File work = new File(base, "alpha");

        engine.cloneRepository(new CloneRequest(url, work, null, false, false), GitProgress.NONE);

        assertEquals("# Beispiel\n", read(new File(work, "README.md")));
        assertEquals("inner\n", read(new File(work, "dir/file.txt")));
        assertEquals("space\n", read(new File(work, "sp ace.txt")));
        assertEquals("umlaut\n", read(new File(work, "ümläut.txt")));
        assertTrue(new File(work, "run.sh").isFile());
        final File link = new File(work, "link");
        assertTrue("Symlink wird als Datei mit dem Linkziel angelegt", link.isFile() && !Files.isSymbolicLink(link.toPath()));
        assertEquals("README.md", read(link));
        assertTrue("Status direkt nach dem Klon sauber", engine.status(work).isClean());
    }

    @Test
    public void commitPushAndUpdateTravelBetweenTwoClones() throws Exception {
        GitFixtures.seedTypicalProject(remote, scratch);
        final File first = new File(base, "first");
        final File second = new File(base, "second");
        engine.cloneRepository(new CloneRequest(url, first, null, false, false), GitProgress.NONE);
        engine.cloneRepository(new CloneRequest(url, second, null, false, false), GitProgress.NONE);

        write(new File(first, "neu.txt"), "neu\n");
        engine.stage(first, List.of("neu.txt"));
        assertEquals(List.of(new ChangedFile("neu.txt", ChangedFile.Kind.ADDED)), engine.status(first).staged());
        engine.commit(new CommitRequest(first, "Neue Datei", IDENTITY, false));
        engine.push(new PushRequest(first, false, false), GitProgress.NONE);

        assertEquals(GitFixtures.branchHead(remote, "main"), headOf(first));
        final UpdateRequest update = new UpdateRequest(second, UpdateRequest.Strategy.MERGE,
                UpdateRequest.ConflictPolicy.ABORT, false);
        assertEquals(UpdateOutcome.FAST_FORWARDED, engine.update(update, GitProgress.NONE));
        assertEquals("neu\n", read(new File(second, "neu.txt")));
        assertTrue(engine.status(second).isClean());
    }

    @Test
    public void aFileNameThatIsForbiddenOnPhoneStorageFailsCleanlyAndLeavesNothingBehind() throws Exception {
        GitFixtures.seedWithFile(remote, scratch, "a:b.txt", "x");
        final File work = new File(base, "alpha");

        final GitFailureException failure = assertThrows(GitFailureException.class, () ->
                engine.cloneRepository(new CloneRequest(url, work, null, false, false), GitProgress.NONE));

        assertEquals(GitFailureKind.INVALID_PATH, failure.kind());
        assertFalse("Der halbe Klon wird entfernt", work.exists());
    }

    private static String headOf(
            final File work
    ) throws Exception {
        return GitFixtures.branchHead(new File(work, ".git"), "main");
    }

    private static String read(
            final File file
    ) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(
            final File file,
            final String content
    ) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void shell(
            final String command
    ) throws IOException {
        final ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand(command);
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            in.readAllBytes();
        }
    }
}
