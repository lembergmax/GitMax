package de.lembergmax.gitmax.repository;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.storage.RepoSettingsStore;
import de.lembergmax.gitmax.storage.WorkspaceStore;

import org.eclipse.jgit.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * Löscht ein Repo samt Arbeitskopie vom Gerät. Auf dem Server bleibt alles bestehen. Ein Arbeitsordner selbst wird nie
 * gelöscht, auch wenn er ein Repo ist: er enthielte dann alle Repos darunter.
 */
public final class RepoRemover {

    /** Warum nicht gelöscht wurde. */
    public enum Reason {
        /** Der Ordner ist kein Git-Repo (mehr). */
        NOT_A_REPO,
        /** Der Ordner ist ein Arbeitsordner. */
        WORKSPACE_ROOT,
        /** Das Dateisystem hat das Löschen abgelehnt; der Ordner kann teilweise gelöscht sein. */
        IO
    }

    /** Das Löschen ist gescheitert. */
    public static final class RemovalException extends Exception {

        private final Reason reason;

        RemovalException(
                @NonNull final Reason reason,
                @NonNull final String message,
                final Throwable cause
        ) {
            super(message, cause);
            this.reason = reason;
        }

        @NonNull
        public Reason reason() {
            return reason;
        }
    }

    private final WorkspaceStore workspace;
    private final RepoSettingsStore settings;

    public RepoRemover(
            @NonNull final WorkspaceStore workspace,
            @NonNull final RepoSettingsStore settings
    ) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Löscht den Ordner rekursiv; blockiert und gehört auf einen Hintergrund-Thread. */
    public void delete(
            @NonNull final File directory
    ) throws RemovalException {
        final File repo = Objects.requireNonNull(directory, "directory").getAbsoluteFile();
        if (!new File(repo, ".git").exists()) {
            throw new RemovalException(Reason.NOT_A_REPO, "Not a Git repo: " + repo, null);
        }
        if (workspace.roots().contains(repo)) {
            throw new RemovalException(Reason.WORKSPACE_ROOT, "A workspace folder is never deleted: " + repo, null);
        }
        try {
            FileUtils.delete(repo, FileUtils.RECURSIVE | FileUtils.RETRY);
        } catch (final IOException failed) {
            throw new RemovalException(Reason.IO, "Delete failed: " + repo, failed);
        }
        settings.forget(repo);
    }
}
