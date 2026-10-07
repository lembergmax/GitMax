package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import de.lembergmax.gitmax.domain.CloneJournal;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.CloneRequest;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Klont ein mehrere Gigabyte großes Repo, das der Rechner per {@code git daemon} bereitstellt
 * ({@code adb reverse tcp:9418 tcp:9418}). Läuft nur, wenn der Test mit {@code -e hugeRepo big2.git} gestartet wird.
 */
public final class JgitHugeCloneDeviceTest {

    @Test
    public void cloneOfAMultiGigabyteRepoOnPhoneStorageSucceeds() throws Exception {
        final String name = InstrumentationRegistry.getArguments().getString("hugeRepo");
        Assume.assumeTrue("kein Riesen-Repo angegeben", name != null);
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        JgitEnvironment.install(context);
        final File work = new File(Environment.getExternalStorageDirectory(), "GitMaxTest/huge-" + UUID.randomUUID());
        final RemoteUrl url = RemoteUrl.parse("https://example.invalid/max/alpha.git").orElseThrow();
        final JgitEngine engine = new JgitEngine(GitCredentials.NONE, JgitEngine::isSharedStoragePath, CloneJournal.NONE,
                IdentitySource.NONE, ignored -> "git://127.0.0.1:9418/" + name);
        final long start = System.currentTimeMillis();
        try {
            engine.cloneRepository(new CloneRequest(url, work, null, false, false), new GitProgress() {
                private GitProgress.Phase last;

                @Override
                public void onProgress(
                        final Phase phase,
                        final float fraction
                ) {
                    if (phase != last) {
                        last = phase;
                        Log.i("HUGE", phase + " nach " + (System.currentTimeMillis() - start) / 1000 + " s");
                    }
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            });
            Log.i("HUGE", "fertig nach " + (System.currentTimeMillis() - start) / 1000 + " s, heap max "
                    + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MiB");

            assertEquals(24L * 1024 * 1024, new File(work, "block000.bin").length());
            assertTrue(engine.status(work).isClean());
        } finally {
            if (work.exists()) {
                try (Stream<java.nio.file.Path> walk = Files.walk(work.toPath())) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
                }
            }
        }
    }
}
