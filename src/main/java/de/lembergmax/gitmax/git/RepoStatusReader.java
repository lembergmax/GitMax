package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.RepoStatus;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Liest den Zustand eines lokalen Repos. {@link #quick(File)} braucht nur Referenzen und ist auch auf
 * dem Telefonspeicher schnell; {@link #full(File)} prüft zusätzlich alle Dateien und kann bei großen
 * Repos dauern.
 */
public final class RepoStatusReader {

    private static final int SHORT_ID_LENGTH = 7;

    /** Branch, Upstream, voraus/zurück. Dateiänderungen bleiben {@link RepoStatus#CHANGES_UNKNOWN}. */
    @NonNull
    public RepoStatus quick(
            @NonNull final File directory
    ) throws IOException {
        Objects.requireNonNull(directory, "directory");
        try (Git git = Git.open(directory)) {
            return quick(git.getRepository());
        }
    }

    /** Wie {@link #quick(File)}, dazu die Prüfung der Arbeitskopie auf Änderungen und Konflikte. */
    @NonNull
    public RepoStatus full(
            @NonNull final File directory
    ) throws IOException, GitAPIException {
        Objects.requireNonNull(directory, "directory");
        try (Git git = Git.open(directory)) {
            return withStatus(quick(git.getRepository()), git.status().call());
        }
    }

    private static RepoStatus quick(
            final Repository repository
    ) throws IOException {
        final Ref head = repository.exactRef(Constants.HEAD);
        final boolean detached = head != null && !head.isSymbolic();
        final String branch = detached ? shortId(head.getObjectId()) : repository.getBranch();

        int ahead = 0;
        int behind = 0;
        boolean hasUpstream = false;
        if (!detached) {
            final BranchTrackingStatus tracking = BranchTrackingStatus.of(repository, branch);
            if (tracking != null) {
                hasUpstream = true;
                ahead = tracking.getAheadCount();
                behind = tracking.getBehindCount();
            }
        }
        return new RepoStatus(branch == null ? "" : branch, detached, hasUpstream, ahead, behind,
                RepoStatus.CHANGES_UNKNOWN, false);
    }

    private static RepoStatus withStatus(
            final RepoStatus quick,
            final Status status
    ) {
        final Set<String> paths = new HashSet<>();
        paths.addAll(status.getAdded());
        paths.addAll(status.getChanged());
        paths.addAll(status.getRemoved());
        paths.addAll(status.getMissing());
        paths.addAll(status.getModified());
        paths.addAll(status.getUntracked());
        paths.addAll(status.getConflicting());
        return quick.withChanges(paths.size(), !status.getConflicting().isEmpty());
    }

    private static String shortId(
            final ObjectId id
    ) {
        return id == null ? "" : id.abbreviate(SHORT_ID_LENGTH).name();
    }
}
