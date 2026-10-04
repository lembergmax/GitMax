package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.ResetMode;

import org.eclipse.jgit.api.CherryPickResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.RevertCommand;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Verlauf umschreiben: zurücksetzen, rückgängig machen, übernehmen. */
final class RewriteOperations {

    private final IdentityConfigurer identity;

    RewriteOperations(
            @NonNull final IdentityConfigurer identity
    ) {
        this.identity = identity;
    }

    void reset(
            @NonNull final File repoDirectory,
            @NonNull final String commitId,
            @NonNull final ResetMode mode
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final ObjectId target = requireCommit(git.getRepository(), commitId);
            final ResetCommand.ResetType type;
            switch (mode) {
                case SOFT:
                    type = ResetCommand.ResetType.SOFT;
                    break;
                case MIXED:
                    type = ResetCommand.ResetType.MIXED;
                    break;
                case HARD:
                default:
                    type = ResetCommand.ResetType.HARD;
                    break;
            }
            git.reset().setMode(type).setRef(target.getName()).call();
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void revert(
            @NonNull final File repoDirectory,
            @NonNull final String commitId
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory); RevWalk walk = new RevWalk(git.getRepository())) {
            identity.ensure(git.getRepository());
            final RevCommit target = walk.parseCommit(requireCommit(git.getRepository(), commitId));
            final RevertCommand command = git.revert().include(target);
            final RevCommit created = command.call();
            if (created == null) {
                final List<String> paths = command.getUnmergedPaths() == null ? List.of() : new ArrayList<>(command.getUnmergedPaths());
                if (!paths.isEmpty()) {
                    throw new GitFailureException(GitFailureKind.CONFLICT,
                            "Conflicts in " + paths.size() + " file(s)", paths, null);
                }
                throw new GitFailureException(GitFailureKind.DIRTY_TREE, "Local changes would be overwritten", null);
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void cherryPick(
            @NonNull final File repoDirectory,
            @NonNull final String commitId
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            identity.ensure(git.getRepository());
            final ObjectId target = requireCommit(git.getRepository(), commitId);
            final CherryPickResult result = git.cherryPick().include(target).call();
            switch (result.getStatus()) {
                case OK:
                    return;
                case CONFLICTING:
                    final Set<String> conflicts = git.status().call().getConflicting();
                    throw new GitFailureException(GitFailureKind.CONFLICT,
                            "Conflicts in " + conflicts.size() + " file(s)", new ArrayList<>(new TreeSet<>(conflicts)), null);
                case FAILED:
                default:
                    throw new GitFailureException(GitFailureKind.DIRTY_TREE, "Local changes would be overwritten", null);
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    private static ObjectId requireCommit(
            final Repository repository,
            final String commitId
    ) throws Exception {
        final ObjectId id = repository.resolve(commitId);
        if (id == null || !repository.getObjectDatabase().has(id)) {
            throw new GitFailureException(GitFailureKind.NOT_FOUND, "Commit not found: " + commitId, null);
        }
        try (RevWalk walk = new RevWalk(repository)) {
            return walk.parseCommit(id).getId();
        } catch (final IncorrectObjectTypeException notACommit) {
            throw new GitFailureException(GitFailureKind.NOT_FOUND, "Not a commit: " + commitId, notACommit);
        }
    }
}
