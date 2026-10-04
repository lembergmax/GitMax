package de.lembergmax.gitmax.git;

import android.content.Context;

import androidx.annotation.NonNull;

import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

import java.io.File;
import java.util.Objects;

/**
 * Richtet JGit einmalig für Android ein: Home-Verzeichnis im App-Speicher, eigener
 * {@link SystemReader} und kleine Cache-Grenzen für den begrenzten Arbeitsspeicher eines Telefons.
 */
public final class JgitEnvironment {

    private static final String HOME_DIRECTORY_NAME = "git-home";

    private static final long PACKED_GIT_LIMIT_BYTES = 32L * 1024 * 1024;
    private static final long DELTA_BASE_CACHE_LIMIT_BYTES = 8L * 1024 * 1024;
    private static final int STREAM_FILE_THRESHOLD_BYTES = 8 * 1024 * 1024;

    private static boolean installed;

    private JgitEnvironment() {
    }

    /**
     * Muss vor der ersten Git-Nutzung laufen; weitere Aufrufe tun nichts.
     */
    public static synchronized void install(
            @NonNull final Context context
    ) {
        Objects.requireNonNull(context, "context");
        if (installed) {
            return;
        }

        final File home = new File(context.getFilesDir(), HOME_DIRECTORY_NAME);
        ensureDirectory(home);

        System.setProperty("user.home", home.getAbsolutePath());
        FS.DETECTED.setUserHome(home);
        SystemReader.setInstance(new AppSystemReader(SystemReader.getInstance(), home));
        installWindowCache();

        installed = true;
    }

    private static void ensureDirectory(
            final File directory
    ) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create the Git home: " + directory.getAbsolutePath());
        }
    }

    private static void installWindowCache() {
        final WindowCacheConfig config = new WindowCacheConfig();
        config.setPackedGitLimit(PACKED_GIT_LIMIT_BYTES);
        config.setDeltaBaseCacheLimit((int) DELTA_BASE_CACHE_LIMIT_BYTES);
        config.setStreamFileThreshold(STREAM_FILE_THRESHOLD_BYTES);
        // Memory-Mapping macht auf dem Telefonspeicher (FUSE) mehr Ärger als Nutzen.
        config.setPackedGitMMAP(false);
        config.install();
    }
}
