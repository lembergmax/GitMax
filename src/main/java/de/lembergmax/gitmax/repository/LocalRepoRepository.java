package de.lembergmax.gitmax.repository;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.RepoNaming;
import de.lembergmax.gitmax.domain.model.LocalRepo;
import de.lembergmax.gitmax.domain.model.RepoStatus;
import de.lembergmax.gitmax.git.RepoOrigins;
import de.lembergmax.gitmax.git.RepoStatusReader;
import de.lembergmax.gitmax.storage.RepoScanner;
import de.lembergmax.gitmax.storage.WorkspaceStore;

import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Die Repos auf dem Gerät: Suche in den Arbeitsordnern und Auslesen ihres Zustands. Alle Methoden
 * blockieren und gehören auf einen Hintergrund-Thread.
 */
public final class LocalRepoRepository {

    private final WorkspaceStore workspace;
    private final RepoStatusReader statusReader;

    public LocalRepoRepository(
            @NonNull final WorkspaceStore workspace,
            @NonNull final RepoStatusReader statusReader
    ) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.statusReader = Objects.requireNonNull(statusReader, "statusReader");
    }

    /** Alle Repos unterhalb der Arbeitsordner, nach Pfad sortiert. */
    @NonNull
    public List<LocalRepo> scan(
            @NonNull final BooleanSupplier cancelled
    ) {
        return RepoScanner.scan(workspace.roots(), cancelled);
    }

    /** Schneller Zustand aus den Referenzen (Branch, voraus, zurück), ohne die Dateien zu prüfen. */
    @NonNull
    public RepoStatus quickStatus(
            @NonNull final LocalRepo repo
    ) throws IOException {
        return statusReader.quick(repo.directory());
    }

    /** Voller Zustand inklusive Prüfung der Arbeitskopie; bei großen Repos langsam. */
    @NonNull
    public RepoStatus fullStatus(
            @NonNull final LocalRepo repo
    ) throws IOException, GitAPIException {
        return statusReader.full(repo.directory());
    }

    /** Woher das Repo stammt (Adresse des Remotes {@code origin}); leer, wenn es keins hat. */
    @NonNull
    public Optional<RemoteUrl> origin(
            @NonNull final LocalRepo repo
    ) {
        return RepoOrigins.originOf(repo.directory());
    }

    /**
     * Was in einem Ordner liegt, in den {@code url} geklont werden soll: nichts, dasselbe Repo oder
     * etwas anderes. Als {@code ClonePlanner.FolderInspector} gedacht.
     */
    @NonNull
    public RepoNaming.FolderState inspectFolder(
            @NonNull final File folder,
            @NonNull final RemoteUrl url
    ) {
        if (!folder.exists()) {
            return RepoNaming.FolderState.MISSING;
        }
        final Optional<RemoteUrl> origin = RepoOrigins.originOf(folder);
        return origin.isPresent() && origin.get().sameRepoAs(url)
                ? RepoNaming.FolderState.SAME_REPO
                : RepoNaming.FolderState.OTHER;
    }
}
