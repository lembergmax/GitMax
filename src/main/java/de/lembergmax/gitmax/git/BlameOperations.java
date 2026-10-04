package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.BlameLine;
import de.lembergmax.gitmax.domain.model.GitFailureKind;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Wer hat welche Zeile einer Datei zuletzt geändert. */
final class BlameOperations {

    /** Mehr Zeilen blamt die App nicht: die Berechnung wächst mit Zeilen mal Verlauf. */
    static final int MAX_LINES = 20_000;

    private static final long MILLIS_PER_SECOND = 1000L;

    BlameOperations() {
    }

    @NonNull
    List<BlameLine> blame(
            @NonNull final File repoDirectory,
            @NonNull final String path
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            if (git.getRepository().resolve("HEAD") == null) {
                throw new GitFailureException(GitFailureKind.NOT_FOUND, "The repo has no commit yet", null);
            }
            final BlameResult result = git.blame().setFilePath(path).setFollowFileRenames(false).call();
            if (result == null) {
                throw new GitFailureException(GitFailureKind.NOT_FOUND, "The file has no history yet: " + path, null);
            }
            final int count = result.getResultContents().size();
            if (count > MAX_LINES) {
                throw new GitFailureException(GitFailureKind.INVALID_PATH, "The file has too many lines for blame: " + count, null);
            }
            result.computeAll();
            final List<BlameLine> lines = new ArrayList<>(count);
            for (int index = 0; index < count; index += 1) {
                final RevCommit commit = result.getSourceCommit(index);
                final PersonIdent author = result.getSourceAuthor(index);
                lines.add(new BlameLine(
                        index + 1,
                        result.getResultContents().getString(index),
                        commit == null ? "" : commit.getName(),
                        author == null ? "" : author.getName(),
                        commit == null ? 0L : commit.getCommitTime() * MILLIS_PER_SECOND,
                        commit == null ? "" : commit.getShortMessage()));
            }
            return lines;
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }
}
