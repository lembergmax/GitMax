package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import org.eclipse.jgit.lib.StoredConfig;

import java.util.Objects;

/**
 * Passt die Repo-Konfiguration an den Telefonspeicher an. Dort gibt es weder Symlinks noch
 * ausführbare Bits, und beides würde sonst beim Auschecken oder bei jedem Status-Lauf Fehler oder
 * scheinbare Änderungen erzeugen.
 */
public final class RepoConfigurator {

    private static final String SECTION_CORE = "core";
    private static final String KEY_SYMLINKS = "symlinks";
    private static final String KEY_FILE_MODE = "filemode";

    private RepoConfigurator() {
    }

    /**
     * Schaltet Symlinks und Dateimodus-Auswertung ab. Symlinks im Repo werden dann als normale Datei
     * mit dem Linkziel als Inhalt ausgecheckt; ein fehlendes Exec-Bit gilt nicht als Änderung.
     */
    public static void applySharedStorageFlags(
            @NonNull final StoredConfig config
    ) {
        Objects.requireNonNull(config, "config");
        config.setBoolean(SECTION_CORE, null, KEY_SYMLINKS, false);
        config.setBoolean(SECTION_CORE, null, KEY_FILE_MODE, false);
    }
}
