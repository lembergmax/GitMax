package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.BlameLine;
import de.lembergmax.gitmax.domain.model.BranchInfo;
import de.lembergmax.gitmax.domain.model.CommitDetail;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.CommitInfo;
import de.lembergmax.gitmax.domain.model.ConflictFile;
import de.lembergmax.gitmax.domain.model.FileDiff;
import de.lembergmax.gitmax.domain.model.LogQuery;
import de.lembergmax.gitmax.domain.model.RemoteInfo;
import de.lembergmax.gitmax.domain.model.RepoStats;
import de.lembergmax.gitmax.domain.model.ResetMode;
import de.lembergmax.gitmax.domain.model.Resolution;
import de.lembergmax.gitmax.domain.model.RunningOperation;
import de.lembergmax.gitmax.domain.model.StashInfo;
import de.lembergmax.gitmax.domain.model.SubmoduleInfo;
import de.lembergmax.gitmax.domain.model.TagInfo;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * {@link GitAdvanced} auf Basis von JGit. Die Fassade verteilt die Aufrufe an kleine Klassen je Bereich
 * (Verlauf, Branches, Tags und Stash, Remotes, Konflikte, Umschreiben, Wartung). Vorgänge, die Commits
 * erzeugen, tragen vorher die Commit-Identität des Repos ein.
 */
public final class JgitAdvanced implements GitAdvanced {

    private final HistoryReader history = new HistoryReader();
    private final BlameOperations blames = new BlameOperations();
    private final BranchOperations branches;
    private final TagAndStashOperations tagsAndStash;
    private final RemoteOperations remotes = new RemoteOperations();
    private final ConflictOperations conflicts;
    private final RewriteOperations rewrites;
    private final ToolOperations tools = new ToolOperations();

    /** Ohne bekannte Identität: für Tests und Aufrufer, die keine Commits erzeugen. */
    public JgitAdvanced() {
        this(IdentitySource.NONE);
    }

    public JgitAdvanced(
            @NonNull final IdentitySource identities
    ) {
        final IdentityConfigurer identity = new IdentityConfigurer(Objects.requireNonNull(identities, "identities"));
        this.branches = new BranchOperations(identity);
        this.tagsAndStash = new TagAndStashOperations(identity);
        this.conflicts = new ConflictOperations(identity);
        this.rewrites = new RewriteOperations(identity);
    }

    @NonNull
    @Override
    public List<CommitInfo> log(
            @NonNull final File repo,
            @NonNull final LogQuery query
    ) throws GitFailureException {
        return history.log(repo, query);
    }

    @NonNull
    @Override
    public CommitDetail commit(
            @NonNull final File repo,
            @NonNull final String commitId
    ) throws GitFailureException {
        return history.commit(repo, commitId);
    }

    @NonNull
    @Override
    public FileDiff diffCommit(
            @NonNull final File repo,
            @NonNull final String commitId,
            @NonNull final String path,
            final boolean ignoreWhitespace
    ) throws GitFailureException {
        return history.diffCommit(repo, commitId, path, ignoreWhitespace);
    }

    @NonNull
    @Override
    public FileDiff diffWorkingTree(
            @NonNull final File repo,
            @NonNull final String path,
            @NonNull final DiffBase base,
            final boolean ignoreWhitespace
    ) throws GitFailureException {
        return history.diffWorkingTree(repo, path, base, ignoreWhitespace);
    }

    @NonNull
    @Override
    public List<BlameLine> blame(
            @NonNull final File repo,
            @NonNull final String path
    ) throws GitFailureException {
        return blames.blame(repo, path);
    }

    @NonNull
    @Override
    public List<BranchInfo> branches(
            @NonNull final File repo
    ) throws GitFailureException {
        return branches.list(repo);
    }

    @Override
    public void createBranch(
            @NonNull final File repo,
            @NonNull final String name,
            @Nullable final String startPoint,
            final boolean checkout
    ) throws GitFailureException {
        branches.create(repo, name, startPoint, checkout);
    }

    @Override
    public void renameBranch(
            @NonNull final File repo,
            @NonNull final String oldName,
            @NonNull final String newName
    ) throws GitFailureException {
        branches.rename(repo, oldName, newName);
    }

    @Override
    public void deleteBranch(
            @NonNull final File repo,
            @NonNull final String name,
            final boolean force
    ) throws GitFailureException {
        branches.delete(repo, name, force);
    }

    @Override
    public void checkoutBranch(
            @NonNull final File repo,
            @NonNull final String name
    ) throws GitFailureException {
        branches.checkoutBranch(repo, name);
    }

    @NonNull
    @Override
    public String checkoutRemoteBranch(
            @NonNull final File repo,
            @NonNull final String remoteBranch
    ) throws GitFailureException {
        return branches.checkoutRemoteBranch(repo, remoteBranch);
    }

    @Override
    public void checkoutCommit(
            @NonNull final File repo,
            @NonNull final String commitOrTag
    ) throws GitFailureException {
        branches.checkoutCommit(repo, commitOrTag);
    }

    @NonNull
    @Override
    public MergeOutcome merge(
            @NonNull final File repo,
            @NonNull final String branch
    ) throws GitFailureException {
        return branches.merge(repo, branch);
    }

    @Override
    public void rebase(
            @NonNull final File repo,
            @NonNull final String onto
    ) throws GitFailureException {
        branches.rebase(repo, onto);
    }

    @NonNull
    @Override
    public List<StashInfo> stashes(
            @NonNull final File repo
    ) throws GitFailureException {
        return tagsAndStash.stashes(repo);
    }

    @Override
    public void stash(
            @NonNull final File repo,
            @NonNull final String message,
            final boolean includeUntracked
    ) throws GitFailureException {
        tagsAndStash.stash(repo, message, includeUntracked);
    }

    @Override
    public void applyStash(
            @NonNull final File repo,
            final int index,
            final boolean drop
    ) throws GitFailureException {
        tagsAndStash.applyStash(repo, index, drop);
    }

    @Override
    public void dropStash(
            @NonNull final File repo,
            final int index
    ) throws GitFailureException {
        tagsAndStash.dropStash(repo, index);
    }

    @NonNull
    @Override
    public List<TagInfo> tags(
            @NonNull final File repo
    ) throws GitFailureException {
        return tagsAndStash.tags(repo);
    }

    @Override
    public void createTag(
            @NonNull final File repo,
            @NonNull final String name,
            @Nullable final String commitId,
            @Nullable final String message,
            @NonNull final CommitIdentity tagger
    ) throws GitFailureException {
        tagsAndStash.createTag(repo, name, commitId, message, tagger);
    }

    @Override
    public void deleteTag(
            @NonNull final File repo,
            @NonNull final String name
    ) throws GitFailureException {
        tagsAndStash.deleteTag(repo, name);
    }

    @NonNull
    @Override
    public List<RemoteInfo> remotes(
            @NonNull final File repo
    ) throws GitFailureException {
        return remotes.list(repo);
    }

    @Override
    public void addRemote(
            @NonNull final File repo,
            @NonNull final String name,
            @NonNull final String url
    ) throws GitFailureException {
        remotes.add(repo, name, url);
    }

    @Override
    public void setRemoteUrl(
            @NonNull final File repo,
            @NonNull final String name,
            @NonNull final String url
    ) throws GitFailureException {
        remotes.setUrl(repo, name, url);
    }

    @Override
    public void renameRemote(
            @NonNull final File repo,
            @NonNull final String oldName,
            @NonNull final String newName
    ) throws GitFailureException {
        remotes.rename(repo, oldName, newName);
    }

    @Override
    public void removeRemote(
            @NonNull final File repo,
            @NonNull final String name
    ) throws GitFailureException {
        remotes.remove(repo, name);
    }

    @NonNull
    @Override
    public RunningOperation runningOperation(
            @NonNull final File repo
    ) throws GitFailureException {
        return conflicts.running(repo);
    }

    @NonNull
    @Override
    public List<ConflictFile> conflicts(
            @NonNull final File repo
    ) throws GitFailureException {
        return conflicts.list(repo);
    }

    @Override
    public void resolveConflict(
            @NonNull final File repo,
            @NonNull final String path,
            @NonNull final Resolution resolution
    ) throws GitFailureException {
        conflicts.resolve(repo, path, resolution);
    }

    @Override
    public void markResolved(
            @NonNull final File repo,
            @NonNull final String path
    ) throws GitFailureException {
        conflicts.markResolved(repo, path);
    }

    @NonNull
    @Override
    public String continueOperation(
            @NonNull final File repo,
            @NonNull final CommitIdentity committer
    ) throws GitFailureException {
        return conflicts.continueOperation(repo, committer);
    }

    @Override
    public void skipRebase(
            @NonNull final File repo
    ) throws GitFailureException {
        conflicts.skipRebase(repo);
    }

    @Override
    public void reset(
            @NonNull final File repo,
            @NonNull final String commitId,
            @NonNull final ResetMode mode
    ) throws GitFailureException {
        rewrites.reset(repo, commitId, mode);
    }

    @Override
    public void revert(
            @NonNull final File repo,
            @NonNull final String commitId
    ) throws GitFailureException {
        rewrites.revert(repo, commitId);
    }

    @Override
    public void cherryPick(
            @NonNull final File repo,
            @NonNull final String commitId
    ) throws GitFailureException {
        rewrites.cherryPick(repo, commitId);
    }

    @NonNull
    @Override
    public List<String> cleanPreview(
            @NonNull final File repo
    ) throws GitFailureException {
        return tools.cleanPreview(repo);
    }

    @Override
    public void clean(
            @NonNull final File repo,
            @NonNull final Collection<String> paths
    ) throws GitFailureException {
        tools.clean(repo, paths);
    }

    @NonNull
    @Override
    public RepoStats stats(
            @NonNull final File repo
    ) throws GitFailureException {
        return tools.stats(repo);
    }

    @Override
    public void collectGarbage(
            @NonNull final File repo
    ) throws GitFailureException {
        tools.gc(repo);
    }

    @NonNull
    @Override
    public String readExclude(
            @NonNull final File repo
    ) throws GitFailureException {
        return tools.readExclude(repo);
    }

    @Override
    public void writeExclude(
            @NonNull final File repo,
            @NonNull final String text
    ) throws GitFailureException {
        tools.writeExclude(repo, text);
    }

    @NonNull
    @Override
    public List<SubmoduleInfo> submodules(
            @NonNull final File repo
    ) throws GitFailureException {
        return tools.submodules(repo);
    }

    @Override
    public boolean pruneOnFetch(
            @NonNull final File repo
    ) throws GitFailureException {
        return tools.pruneOnFetch(repo);
    }

    @Override
    public void setPruneOnFetch(
            @NonNull final File repo,
            final boolean prune
    ) throws GitFailureException {
        tools.setPruneOnFetch(repo, prune);
    }
}
