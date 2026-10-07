package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.BranchInfo;
import de.lembergmax.gitmax.domain.model.GitFailureKind;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Branches auflisten, anlegen, umbenennen, löschen, auschecken, zusammenführen und aufsetzen. */
final class BranchOperations {

    private final IdentityConfigurer identity;

    BranchOperations(
            @NonNull final IdentityConfigurer identity
    ) {
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    @NonNull
    List<BranchInfo> list(
            @NonNull final File repoDirectory
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory); RevWalk walk = new RevWalk(git.getRepository())) {
            final Repository repository = git.getRepository();
            final String fullBranch = repository.getFullBranch();
            final List<BranchInfo> branches = new ArrayList<>();
            for (final Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS)) {
                final String name = Repository.shortenRefName(ref.getName());
                final BranchTrackingStatus tracking = BranchTrackingStatus.of(repository, name);
                branches.add(new BranchInfo(name, false, ref.getName().equals(fullBranch),
                        tracking == null ? upstreamName(repository, name) : Repository.shortenRefName(tracking.getRemoteTrackingBranch()),
                        tracking == null ? 0 : tracking.getAheadCount(), tracking == null ? 0 : tracking.getBehindCount(),
                        ref.getObjectId().getName(), timeOf(walk, ref.getObjectId())));
            }
            for (final Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_REMOTES)) {
                if (ref.getName().endsWith("/" + Constants.HEAD) || ref.getObjectId() == null) {
                    continue;
                }
                branches.add(new BranchInfo(Repository.shortenRefName(ref.getName()), true, false, null, 0, 0,
                        ref.getObjectId().getName(), timeOf(walk, ref.getObjectId())));
            }
            branches.sort(Comparator.comparing((BranchInfo branch) -> branch.remote())
                    .thenComparing(branch -> !branch.current())
                    .thenComparing(BranchInfo::name, String.CASE_INSENSITIVE_ORDER));
            return branches;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void create(
            @NonNull final File repoDirectory,
            @NonNull final String name,
            @Nullable final String startPoint,
            final boolean checkout
    ) throws GitFailureException {
        requireValidName(Constants.R_HEADS + name, name);
        try (Git git = JgitEngine.open(repoDirectory)) {
            final CreateBranchCommand command = git.branchCreate().setName(name);
            if (startPoint != null) {
                command.setStartPoint(startPoint);
            }
            command.call();
            if (checkout) {
                git.checkout().setName(name).call();
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void rename(
            @NonNull final File repoDirectory,
            @NonNull final String oldName,
            @NonNull final String newName
    ) throws GitFailureException {
        requireValidName(Constants.R_HEADS + newName, newName);
        try (Git git = JgitEngine.open(repoDirectory)) {
            git.branchRename().setOldName(oldName).setNewName(newName).call();
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void delete(
            @NonNull final File repoDirectory,
            @NonNull final String name,
            final boolean force
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            if (name.equals(git.getRepository().getBranch())) {
                throw new GitFailureException(GitFailureKind.CURRENT_BRANCH,
                        "The current branch cannot be deleted: " + name, null);
            }
            git.branchDelete().setBranchNames(name).setForce(force).call();
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void checkoutBranch(
            @NonNull final File repoDirectory,
            @NonNull final String name
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            git.checkout().setName(name).call();
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    @NonNull
    String checkoutRemoteBranch(
            @NonNull final File repoDirectory,
            @NonNull final String remoteBranch
    ) throws GitFailureException {
        final int slash = remoteBranch.indexOf('/');
        if (slash < 0 || slash == remoteBranch.length() - 1) {
            throw new GitFailureException(GitFailureKind.INVALID_NAME, "Not a remote branch: " + remoteBranch, null);
        }
        final String local = remoteBranch.substring(slash + 1);
        try (Git git = JgitEngine.open(repoDirectory)) {
            if (git.getRepository().findRef(Constants.R_HEADS + local) != null) {
                git.checkout().setName(local).call();
            } else {
                git.checkout()
                        .setCreateBranch(true)
                        .setName(local)
                        .setStartPoint(Constants.R_REMOTES + remoteBranch)
                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                        .call();
            }
            return local;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void checkoutCommit(
            @NonNull final File repoDirectory,
            @NonNull final String commitOrTag
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final ObjectId target = git.getRepository().resolve(commitOrTag + "^{commit}");
            if (target == null) {
                throw new GitFailureException(GitFailureKind.NOT_FOUND, "Not found: " + commitOrTag, null);
            }
            git.checkout().setName(target.getName()).call();
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    @NonNull
    GitAdvanced.MergeOutcome merge(
            @NonNull final File repoDirectory,
            @NonNull final String branch
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            identity.ensure(git.getRepository());
            final Ref ref = git.getRepository().findRef(branch);
            if (ref == null) {
                throw new GitFailureException(GitFailureKind.NOT_FOUND, "Branch not found: " + branch, null);
            }
            // Ein Konflikt wird mit „reset --hard“ zurückgenommen und risse unbeteiligte lokale Änderungen mit. Wie beim Update
            // beginnt ein echter Merge deshalb nur bei sauberem Arbeitsbaum; vorspulen geht auch mit lokalen Änderungen.
            final boolean dirty = git.status().call().hasUncommittedChanges();
            final MergeResult result = git.merge()
                    .include(ref)
                    .setFastForward(dirty ? MergeCommand.FastForwardMode.FF_ONLY : MergeCommand.FastForwardMode.FF)
                    .setCommit(true)
                    .setMessage("Merge branch '" + branch + "'")
                    .call();
            switch (result.getMergeStatus()) {
                case ABORTED:
                    throw new GitFailureException(dirty ? GitFailureKind.DIRTY_TREE : GitFailureKind.UNKNOWN,
                            dirty ? "Local changes prevent the merge" : "Merge not possible: " + result.getMergeStatus(), null);
                case ALREADY_UP_TO_DATE:
                    return GitAdvanced.MergeOutcome.ALREADY_UP_TO_DATE;
                case FAST_FORWARD:
                    return GitAdvanced.MergeOutcome.FAST_FORWARDED;
                case MERGED:
                    return GitAdvanced.MergeOutcome.MERGED;
                case CONFLICTING:
                    final Set<String> paths = new TreeSet<>(result.getConflicts().keySet());
                    throw new GitFailureException(GitFailureKind.CONFLICT,
                            "Conflicts in " + paths.size() + " file(s)", new ArrayList<>(paths), null);
                case CHECKOUT_CONFLICT:
                case FAILED:
                    final Set<String> blocking = new TreeSet<>();
                    if (result.getCheckoutConflicts() != null) {
                        blocking.addAll(result.getCheckoutConflicts());
                    }
                    if (result.getFailingPaths() != null) {
                        blocking.addAll(result.getFailingPaths().keySet());
                    }
                    throw new GitFailureException(GitFailureKind.DIRTY_TREE,
                            "Local changes would be overwritten", new ArrayList<>(blocking), null);
                default:
                    throw new GitFailureException(GitFailureKind.UNKNOWN,
                            "Merge not possible: " + result.getMergeStatus(), null);
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void rebase(
            @NonNull final File repoDirectory,
            @NonNull final String onto
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            identity.ensure(git.getRepository());
            final RebaseResult result = git.rebase().setUpstream(onto).call();
            switch (result.getStatus()) {
                case OK:
                case UP_TO_DATE:
                case FAST_FORWARD:
                    return;
                case UNCOMMITTED_CHANGES:
                    throw new GitFailureException(GitFailureKind.DIRTY_TREE, "Local changes prevent the rebase", null);
                case CONFLICTS:
                case STOPPED:
                    final List<String> paths = result.getConflicts() == null ? List.of() : new ArrayList<>(result.getConflicts());
                    throw new GitFailureException(GitFailureKind.CONFLICT,
                            "Conflicts while rebasing in " + paths.size() + " file(s)", paths, null);
                default:
                    throw new GitFailureException(GitFailureKind.UNKNOWN, "Rebase not possible: " + result.getStatus(), null);
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    private static void requireValidName(
            final String fullName,
            final String name
    ) throws GitFailureException {
        if (name.isBlank() || !Repository.isValidRefName(fullName) || name.contains(" ") || name.startsWith("-")) {
            throw new GitFailureException(GitFailureKind.INVALID_NAME, "Invalid name: " + name, null);
        }
    }

    @Nullable
    private static String upstreamName(
            final Repository repository,
            final String branch
    ) {
        final String tracking = new BranchConfig(repository.getConfig(), branch).getRemoteTrackingBranch();
        return tracking == null ? null : Repository.shortenRefName(tracking);
    }

    private static long timeOf(
            final RevWalk walk,
            final ObjectId id
    ) throws IOException {
        final RevCommit commit = walk.parseCommit(id);
        return commit.getCommitterIdent().getWhen().getTime();
    }
}
