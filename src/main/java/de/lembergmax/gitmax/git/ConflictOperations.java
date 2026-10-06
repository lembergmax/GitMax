package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.ConflictFile;
import de.lembergmax.gitmax.domain.model.ConflictKind;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.Resolution;
import de.lembergmax.gitmax.domain.model.RunningOperation;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseCommand;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Konflikte listen und auflösen, angefangene Merges, Rebases, Cherry-picks und Reverts abschließen. */
final class ConflictOperations {

    private static final int STAGE_OURS = 2;
    private static final int STAGE_THEIRS = 3;
    private static final long MAX_SCAN_BYTES = 2L * 1024 * 1024;

    private final IdentityConfigurer identity;

    ConflictOperations(
            @NonNull final IdentityConfigurer identity
    ) {
        this.identity = identity;
    }

    @NonNull
    RunningOperation running(
            @NonNull final File repoDirectory
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final RepositoryState state = git.getRepository().getRepositoryState();
            if (state.isRebasing()) {
                return RunningOperation.REBASE;
            }
            if (state == RepositoryState.MERGING || state == RepositoryState.MERGING_RESOLVED) {
                return RunningOperation.MERGE;
            }
            if (isCherryPicking(state)) {
                return RunningOperation.CHERRY_PICK;
            }
            if (state == RepositoryState.REVERTING || state == RepositoryState.REVERTING_RESOLVED) {
                return RunningOperation.REVERT;
            }
            return RunningOperation.NONE;
        }
    }

    @NonNull
    List<ConflictFile> list(
            @NonNull final File repoDirectory
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final Repository repository = git.getRepository();
            final Map<String, IndexDiff.StageState> states = git.status().call().getConflictingStageState();
            final List<ConflictFile> files = new ArrayList<>();
            for (final Map.Entry<String, IndexDiff.StageState> entry : states.entrySet()) {
                files.add(new ConflictFile(entry.getKey(), kindOf(entry.getValue()),
                        hasMarkers(new File(repository.getWorkTree(), entry.getKey()))));
            }
            files.sort(Comparator.comparing(ConflictFile::path));
            return files;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void resolve(
            @NonNull final File repoDirectory,
            @NonNull final String path,
            @NonNull final Resolution resolution
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final Repository repository = git.getRepository();
            switch (resolution) {
                case OURS:
                    takeSide(git, path, CheckoutCommand.Stage.OURS, STAGE_OURS);
                    break;
                case THEIRS:
                    takeSide(git, path, CheckoutCommand.Stage.THEIRS, STAGE_THEIRS);
                    break;
                case BOTH:
                default:
                    keepBoth(git, repository, path);
                    break;
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void markResolved(
            @NonNull final File repoDirectory,
            @NonNull final String path
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            stagePath(git, path);
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    /**
     * Schließt den angefangenen Vorgang ab: Merge, Cherry-pick und Revert mit einem Commit, Rebase mit
     * dem nächsten Schritt.
     *
     * @return gekürzte Kennung des neuen Commits, leer wenn nur ein Rebase-Schritt weiterlief
     */
    @NonNull
    String continueOperation(
            @NonNull final File repoDirectory,
            @NonNull final CommitIdentity committer
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final Repository repository = git.getRepository();
            identity.ensure(repository);
            final RepositoryState state = repository.getRepositoryState();
            if (state.isRebasing()) {
                return continueRebase(git, RebaseCommand.Operation.CONTINUE);
            }
            if (state == RepositoryState.SAFE) {
                throw new GitFailureException(GitFailureKind.NOTHING_TO_COMMIT, "Nothing is in progress that could be continued", null);
            }
            final Set<String> open = git.status().call().getConflicting();
            if (!open.isEmpty()) {
                throw new GitFailureException(GitFailureKind.CONFLICT, "There are unresolved conflicts",
                        new ArrayList<>(new TreeSet<>(open)), null);
            }
            final String message = repository.readMergeCommitMsg();
            final PersonIdent person = new PersonIdent(committer.name(), committer.email());
            final RevCommit created = git.commit()
                    .setMessage(message == null || message.isBlank() ? "Merge" : message.strip())
                    .setCommitter(person)
                    .setAuthor(isCherryPicking(state) ? cherryPickedAuthor(repository, person) : person)
                    .setAllowEmpty(true)
                    .call();
            return created.abbreviate(7).name();
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    /** Überspringt den aktuellen Commit eines laufenden Rebases. */
    void skipRebase(
            @NonNull final File repoDirectory
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            continueRebase(git, RebaseCommand.Operation.SKIP);
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    /* ------------------------------------------------------------------------------------------ */

    private static String continueRebase(
            final Git git,
            final RebaseCommand.Operation operation
    ) throws Exception {
        final RebaseResult result = git.rebase().setOperation(operation).call();
        switch (result.getStatus()) {
            case OK:
            case UP_TO_DATE:
            case FAST_FORWARD:
                return "";
            case CONFLICTS:
            case STOPPED:
                final List<String> paths = result.getConflicts() == null ? List.of() : new ArrayList<>(result.getConflicts());
                throw new GitFailureException(GitFailureKind.CONFLICT,
                        "More conflicts while rebasing in " + paths.size() + " file(s)", paths, null);
            case UNCOMMITTED_CHANGES:
                throw new GitFailureException(GitFailureKind.DIRTY_TREE, "Local changes prevent the rebase", null);
            default:
                throw new GitFailureException(GitFailureKind.UNKNOWN, "Rebase not possible: " + result.getStatus(), null);
        }
    }

    private static boolean isCherryPicking(
            final RepositoryState state
    ) {
        return state == RepositoryState.CHERRY_PICKING || state == RepositoryState.CHERRY_PICKING_RESOLVED;
    }

    /** Ein übernommener Commit behält seinen Autor; ohne lesbaren {@code CHERRY_PICK_HEAD} gilt {@code fallback}. */
    private static PersonIdent cherryPickedAuthor(
            final Repository repository,
            final PersonIdent fallback
    ) throws IOException {
        final ObjectId picked = repository.readCherryPickHead();
        if (picked == null) {
            return fallback;
        }
        try (RevWalk walk = new RevWalk(repository)) {
            return walk.parseCommit(picked).getAuthorIdent();
        }
    }

    private static void takeSide(
            final Git git,
            final String path,
            final CheckoutCommand.Stage stage,
            final int stageNumber
    ) throws Exception {
        if (hasStage(git.getRepository().readDirCache(), path, stageNumber)) {
            git.checkout().setStage(stage).addPath(path).call();
            git.add().addFilepattern(path).call();
        } else {
            // Diese Seite hat die Datei gelöscht (oder nie gehabt): sie zu übernehmen heißt löschen.
            git.rm().addFilepattern(path).call();
        }
    }

    private static void keepBoth(
            final Git git,
            final Repository repository,
            final String path
    ) throws Exception {
        final File file = new File(repository.getWorkTree(), path);
        if (file.isFile() && file.length() <= MAX_SCAN_BYTES) {
            // Als ISO-8859-1 gelesen und geschrieben bleibt jedes Byte, wie es war: Die Marken sind ASCII, und eine Datei in
            // einer anderen Kodierung als UTF-8 bekäme beim Umweg über UTF-8 für jedes fremde Byte ein Ersatzzeichen.
            final String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
            if (ConflictMarkers.hasMarkers(text)) {
                Files.write(file.toPath(), ConflictMarkers.keepBoth(text).getBytes(StandardCharsets.ISO_8859_1));
            }
        }
        stagePath(git, path);
    }

    private static void stagePath(
            final Git git,
            final String path
    ) throws Exception {
        if (new File(git.getRepository().getWorkTree(), path).exists()) {
            git.add().addFilepattern(path).call();
        } else {
            git.rm().addFilepattern(path).call();
        }
    }

    private static boolean hasStage(
            final DirCache cache,
            final String path,
            final int stageNumber
    ) {
        // Die Einträge eines Pfads liegen nach Stufe sortiert hintereinander; findEntry liefert den ersten.
        final int first = cache.findEntry(path);
        if (first < 0) {
            return false;
        }
        for (int index = first; index < cache.getEntryCount(); index += 1) {
            final DirCacheEntry entry = cache.getEntry(index);
            if (!entry.getPathString().equals(path)) {
                return false;
            }
            if (entry.getStage() == stageNumber) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMarkers(
            final File file
    ) {
        if (!file.isFile() || file.length() > MAX_SCAN_BYTES) {
            return false;
        }
        try {
            return ConflictMarkers.hasMarkers(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (final IOException unreadable) {
            return false;
        }
    }

    private static ConflictKind kindOf(
            final IndexDiff.StageState state
    ) {
        switch (state) {
            case BOTH_ADDED:
                return ConflictKind.BOTH_ADDED;
            case BOTH_DELETED:
                return ConflictKind.BOTH_DELETED;
            case DELETED_BY_US:
                return ConflictKind.DELETED_BY_US;
            case DELETED_BY_THEM:
                return ConflictKind.DELETED_BY_THEM;
            case ADDED_BY_US:
                return ConflictKind.ADDED_BY_US;
            case ADDED_BY_THEM:
                return ConflictKind.ADDED_BY_THEM;
            case BOTH_MODIFIED:
            default:
                return ConflictKind.BOTH_MODIFIED;
        }
    }
}
