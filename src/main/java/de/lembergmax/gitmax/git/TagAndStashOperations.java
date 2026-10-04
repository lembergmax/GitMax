package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.StashInfo;
import de.lembergmax.gitmax.domain.model.TagInfo;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Tags und Stash eines Repos. */
final class TagAndStashOperations {

    private static final long MILLIS_PER_SECOND = 1000L;

    private final IdentityConfigurer identity;

    TagAndStashOperations(
            @NonNull final IdentityConfigurer identity
    ) {
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    /* Tags */

    @NonNull
    List<TagInfo> tags(
            @NonNull final File repoDirectory
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory); RevWalk walk = new RevWalk(git.getRepository())) {
            final Repository repository = git.getRepository();
            final List<TagInfo> tags = new ArrayList<>();
            for (final Ref ref : git.tagList().call()) {
                final RevObject object = walk.parseAny(ref.getObjectId());
                final String name = Repository.shortenRefName(ref.getName());
                if (object instanceof RevTag tag) {
                    final ObjectId target = repository.getRefDatabase().peel(ref).getPeeledObjectId();
                    final PersonIdent tagger = tag.getTaggerIdent();
                    tags.add(new TagInfo(name, (target == null ? tag.getObject() : target).getName(), true,
                            tag.getFullMessage().strip(), tagger == null ? 0L : tagger.getWhen().getTime()));
                } else if (object instanceof RevCommit commit) {
                    tags.add(new TagInfo(name, commit.getName(), false, "",
                            commit.getCommitterIdent().getWhen().getTime()));
                } else {
                    tags.add(new TagInfo(name, object.getName(), false, "", 0L));
                }
            }
            tags.sort(Comparator.comparing(TagInfo::name, String.CASE_INSENSITIVE_ORDER));
            return tags;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void createTag(
            @NonNull final File repoDirectory,
            @NonNull final String name,
            @Nullable final String commitId,
            @Nullable final String message,
            @NonNull final CommitIdentity tagger
    ) throws GitFailureException {
        if (name.isBlank() || name.contains(" ") || !Repository.isValidRefName(Constants.R_TAGS + name)) {
            throw new GitFailureException(GitFailureKind.INVALID_NAME, "Invalid tag name: " + name, null);
        }
        try (Git git = JgitEngine.open(repoDirectory); RevWalk walk = new RevWalk(git.getRepository())) {
            final ObjectId target = git.getRepository().resolve((commitId == null ? Constants.HEAD : commitId) + "^{commit}");
            if (target == null) {
                throw new GitFailureException(GitFailureKind.NOT_FOUND, "Commit not found", null);
            }
            final boolean annotated = message != null && !message.isBlank();
            final org.eclipse.jgit.api.TagCommand command = git.tag()
                    .setName(name)
                    .setObjectId(walk.parseCommit(target))
                    .setAnnotated(annotated)
                    .setSigned(false);
            if (annotated) {
                command.setMessage(message.strip()).setTagger(new PersonIdent(tagger.name(), tagger.email()));
            }
            command.call();
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void deleteTag(
            @NonNull final File repoDirectory,
            @NonNull final String name
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            git.tagDelete().setTags(name).call();
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    /* Stash */

    @NonNull
    List<StashInfo> stashes(
            @NonNull final File repoDirectory
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final Collection<RevCommit> commits = git.stashList().call();
            final List<StashInfo> stashes = new ArrayList<>(commits.size());
            int index = 0;
            for (final RevCommit commit : commits) {
                stashes.add(new StashInfo(index, commit.getName(), commit.getFullMessage().strip(),
                        commit.getCommitTime() * MILLIS_PER_SECOND));
                index += 1;
            }
            return stashes;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void stash(
            @NonNull final File repoDirectory,
            @NonNull final String message,
            final boolean includeUntracked
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            identity.ensure(git.getRepository());
            final RevCommit created = git.stashCreate()
                    .setIncludeUntracked(includeUntracked)
                    .setWorkingDirectoryMessage(message.isBlank() ? "Stashed changes" : message.strip())
                    .call();
            if (created == null) {
                throw new GitFailureException(GitFailureKind.NOTHING_TO_COMMIT, "There is nothing to stash", null);
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void applyStash(
            @NonNull final File repoDirectory,
            final int index,
            final boolean drop
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            git.stashApply().setStashRef("stash@{" + index + "}").call();
            if (drop) {
                git.stashDrop().setStashRef(index).call();
            }
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void dropStash(
            @NonNull final File repoDirectory,
            final int index
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            git.stashDrop().setStashRef(index).call();
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }
}
