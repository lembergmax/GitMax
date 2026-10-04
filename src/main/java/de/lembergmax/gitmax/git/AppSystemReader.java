package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * Liest Git-Konfiguration ausschließlich aus dem App-Verzeichnis. Auf Android gibt es keine
 * System-Gitconfig und kein Home-Verzeichnis des Nutzers; JGit würde sonst versuchen, ein
 * {@code git}-Programm zu starten, um den Pfad zu ermitteln. Dieselbe Abschottung hält Tests auf dem
 * Entwicklerrechner von dessen globaler Konfiguration ({@code core.autocrlf} u. a.) fern.
 */
public final class AppSystemReader extends SystemReader.Delegate {

    private static final String SYSTEM_CONFIG_NAME = "gitconfig-system";
    private static final String USER_CONFIG_NAME = ".gitconfig";
    private static final String JGIT_CONFIG_NAME = ".jgitconfig";
    private static final String SECTION_GC = "gc";
    private static final String KEY_GC_AUTO = "auto";
    private static final String KEY_GC_AUTO_DETACH = "autoDetach";

    private final File home;

    public AppSystemReader(
            @NonNull final SystemReader delegate,
            @NonNull final File home
    ) {
        super(delegate);
        this.home = Objects.requireNonNull(home, "home");
    }

    @Override
    public FileBasedConfig openSystemConfig(
            final Config parent,
            final FS fs
    ) {
        // Nie angelegt: eine nicht vorhandene Datei ist eine leere Konfiguration.
        return new FileBasedConfig(parent, new File(home, SYSTEM_CONFIG_NAME), fs);
    }

    @Override
    public FileBasedConfig openUserConfig(
            final Config parent,
            final FS fs
    ) {
        return new FileBasedConfig(parent, new File(home, USER_CONFIG_NAME), fs) {
            @Override
            public void load() throws IOException, ConfigInvalidException {
                super.load();
                disableAutomaticGc(this);
            }
        };
    }

    @Override
    public FileBasedConfig openJGitConfig(
            final Config parent,
            final FS fs
    ) {
        return new FileBasedConfig(parent, new File(home, JGIT_CONFIG_NAME), fs);
    }

    @Override
    public StoredConfig getSystemConfig() {
        return openSystemConfig(null, FS.DETECTED);
    }

    @Override
    public StoredConfig getUserConfig() throws IOException, ConfigInvalidException {
        final FileBasedConfig config = openUserConfig(getSystemConfig(), FS.DETECTED);
        config.load();
        return config;
    }

    @Override
    public StoredConfig getJGitConfig() throws IOException, ConfigInvalidException {
        final FileBasedConfig config = openJGitConfig(null, FS.DETECTED);
        config.load();
        return config;
    }

    /**
     * JGits {@code GC} nutzt {@code java.lang.ProcessHandle}, das es auf Android nicht gibt. Ein
     * automatischer Lauf würde mit einem {@code NoClassDefFoundError} abstürzen, deshalb bleibt er aus.
     */
    private static void disableAutomaticGc(
            final Config config
    ) {
        config.setInt(SECTION_GC, null, KEY_GC_AUTO, 0);
        config.setBoolean(SECTION_GC, null, KEY_GC_AUTO_DETACH, false);
    }
}
