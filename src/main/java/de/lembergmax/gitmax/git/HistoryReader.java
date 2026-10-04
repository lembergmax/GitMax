package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.CommitDetail;
import de.lembergmax.gitmax.domain.model.CommitInfo;
import de.lembergmax.gitmax.domain.model.DiffHunk;
import de.lembergmax.gitmax.domain.model.DiffLine;
import de.lembergmax.gitmax.domain.model.FileChange;
import de.lembergmax.gitmax.domain.model.FileDiff;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.LogQuery;
import de.lembergmax.gitmax.domain.model.RefLabel;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.NullOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Liest Verlauf, Commit-Details und Diffs eines Repos. */
final class HistoryReader {

    /** Blobs über dieser Größe bekommen keinen Zeilen-Diff. */
    static final int MAX_DIFF_BYTES = 1024 * 1024;

    private static final int CONTEXT_LINES = 3;

    HistoryReader() {
    }

    @NonNull
    List<CommitInfo> log(
            @NonNull final File repoDirectory,
            @NonNull final LogQuery query
    ) throws GitFailureException {
        try (org.eclipse.jgit.api.Git git = JgitEngine.open(repoDirectory)) {
            final Repository repository = git.getRepository();
            final Map<ObjectId, List<RefLabel>> labels = labelsOf(repository);
            final List<CommitInfo> result = new ArrayList<>();
            try (RevWalk walk = new RevWalk(repository)) {
                if (!markStarts(repository, walk, query)) {
                    return result;
                }
                if (!query.path().isEmpty()) {
                    walk.setTreeFilter(AndTreeFilter.create(PathFilter.create(query.path()), TreeFilter.ANY_DIFF));
                }
                if (!query.author().isEmpty() || !query.text().isEmpty()) {
                    walk.setRevFilter(new ContainsFilter(query.author(), query.text()));
                }
                int skipped = 0;
                for (final RevCommit commit : walk) {
                    if (skipped < query.skip()) {
                        skipped += 1;
                        continue;
                    }
                    if (result.size() >= query.limit()) {
                        break;
                    }
                    result.add(infoOf(commit, labels));
                }
            }
            return result;
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    @NonNull
    CommitDetail commit(
            @NonNull final File repoDirectory,
            @NonNull final String commitId
    ) throws GitFailureException {
        try (org.eclipse.jgit.api.Git git = JgitEngine.open(repoDirectory)) {
            final Repository repository = git.getRepository();
            final Map<ObjectId, List<RefLabel>> labels = labelsOf(repository);
            try (RevWalk walk = new RevWalk(repository); ObjectReader reader = repository.newObjectReader();
                 DiffFormatter formatter = formatter(repository, NullOutputStream.INSTANCE, false, null, true)) {
                final RevCommit commit = parse(repository, walk, commitId);
                final List<FileChange> files = new ArrayList<>();
                for (final DiffEntry entry : formatter.scan(parentTree(reader, walk, commit), treeOf(reader, commit))) {
                    files.add(changeOf(formatter, entry));
                }
                files.sort(Comparator.comparing(FileChange::path, String.CASE_INSENSITIVE_ORDER));
                return new CommitDetail(infoOf(commit, labels), files);
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    @NonNull
    FileDiff diffCommit(
            @NonNull final File repoDirectory,
            @NonNull final String commitId,
            @NonNull final String path,
            final boolean ignoreWhitespace
    ) throws GitFailureException {
        try (org.eclipse.jgit.api.Git git = JgitEngine.open(repoDirectory)) {
            final Repository repository = git.getRepository();
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (RevWalk walk = new RevWalk(repository); ObjectReader reader = repository.newObjectReader();
                 DiffFormatter formatter = formatter(repository, out, ignoreWhitespace, path, false)) {
                final RevCommit commit = parse(repository, walk, commitId);
                return diffOf(repository, formatter, out,
                        formatter.scan(parentTree(reader, walk, commit), treeOf(reader, commit)), path);
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    @NonNull
    FileDiff diffWorkingTree(
            @NonNull final File repoDirectory,
            @NonNull final String path,
            @NonNull final GitAdvanced.DiffBase base,
            final boolean ignoreWhitespace
    ) throws GitFailureException {
        try (org.eclipse.jgit.api.Git git = JgitEngine.open(repoDirectory)) {
            final Repository repository = git.getRepository();
            final DirCache cache = repository.readDirCache();
            if (base == GitAdvanced.DiffBase.WORKTREE_VS_INDEX && cache.findEntry(path) < 0) {
                return newFileDiff(new File(repository.getWorkTree(), path), path);
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ObjectReader reader = repository.newObjectReader();
                 DiffFormatter formatter = formatter(repository, out, ignoreWhitespace, path, false)) {
                final AbstractTreeIterator older;
                final AbstractTreeIterator newer;
                if (base == GitAdvanced.DiffBase.INDEX_VS_HEAD) {
                    older = headTree(repository, reader);
                    newer = new DirCacheIterator(cache);
                } else {
                    older = new DirCacheIterator(cache);
                    newer = new FileTreeIterator(repository);
                }
                return diffOf(repository, formatter, out, formatter.scan(older, newer), path);
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    /* ------------------------------------------------------------------------------------------ */

    private static boolean markStarts(
            final Repository repository,
            final RevWalk walk,
            final LogQuery query
    ) throws IOException {
        if (query.all()) {
            boolean any = false;
            for (final Ref ref : repository.getRefDatabase().getRefs()) {
                final String name = ref.getName();
                if (name.startsWith(Constants.R_HEADS) || name.startsWith(Constants.R_REMOTES) || name.startsWith(Constants.R_TAGS)) {
                    final ObjectId target = peeled(repository, ref);
                    if (target != null) {
                        walk.markStart(walk.parseCommit(target));
                        any = true;
                    }
                }
            }
            return any;
        }
        final ObjectId start = repository.resolve(query.ref() == null ? Constants.HEAD : query.ref());
        if (start == null) {
            return false;
        }
        walk.markStart(walk.parseCommit(start));
        return true;
    }

    private static RevCommit parse(
            final Repository repository,
            final RevWalk walk,
            final String commitId
    ) throws IOException, GitFailureException {
        final ObjectId id = repository.resolve(commitId);
        if (id == null || !repository.getObjectDatabase().has(id)) {
            throw new GitFailureException(GitFailureKind.NOT_FOUND, "Commit not found: " + commitId, null);
        }
        return walk.parseCommit(id);
    }

    private static AbstractTreeIterator treeOf(
            final ObjectReader reader,
            final RevCommit commit
    ) throws IOException {
        final CanonicalTreeParser parser = new CanonicalTreeParser();
        parser.reset(reader, commit.getTree());
        return parser;
    }

    private static AbstractTreeIterator parentTree(
            final ObjectReader reader,
            final RevWalk walk,
            final RevCommit commit
    ) throws IOException {
        if (commit.getParentCount() == 0) {
            return new EmptyTreeIterator();
        }
        final RevCommit parent = walk.parseCommit(commit.getParent(0));
        return treeOf(reader, parent);
    }

    private static AbstractTreeIterator headTree(
            final Repository repository,
            final ObjectReader reader
    ) throws IOException {
        final ObjectId head = repository.resolve(Constants.HEAD + "^{tree}");
        if (head == null) {
            return new EmptyTreeIterator();
        }
        final CanonicalTreeParser parser = new CanonicalTreeParser();
        parser.reset(reader, head);
        return parser;
    }

    private static DiffFormatter formatter(
            final Repository repository,
            final java.io.OutputStream out,
            final boolean ignoreWhitespace,
            @Nullable final String path,
            final boolean detectRenames
    ) {
        final DiffFormatter formatter = new DiffFormatter(out);
        formatter.setRepository(repository);
        formatter.setContext(CONTEXT_LINES);
        formatter.setBinaryFileThreshold(MAX_DIFF_BYTES);
        formatter.setDetectRenames(detectRenames);
        if (ignoreWhitespace) {
            formatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
        }
        if (path != null) {
            formatter.setPathFilter(PathFilter.create(path));
        }
        return formatter;
    }

    private static FileChange changeOf(
            final DiffFormatter formatter,
            final DiffEntry entry
    ) throws IOException {
        final FileHeader header = formatter.toFileHeader(entry);
        final boolean binary = header.getPatchType() == FileHeader.PatchType.BINARY;
        int added = 0;
        int removed = 0;
        if (!binary) {
            for (final Edit edit : header.toEditList()) {
                added += edit.getEndB() - edit.getBeginB();
                removed += edit.getEndA() - edit.getBeginA();
            }
        }
        final String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE ? entry.getOldPath() : entry.getNewPath();
        final FileChange.Kind kind;
        switch (entry.getChangeType()) {
            case ADD:
            case COPY:
                kind = FileChange.Kind.ADDED;
                break;
            case DELETE:
                kind = FileChange.Kind.DELETED;
                break;
            case RENAME:
                kind = FileChange.Kind.RENAMED;
                break;
            case MODIFY:
            default:
                kind = FileChange.Kind.MODIFIED;
                break;
        }
        return new FileChange(path, entry.getOldPath().equals(DiffEntry.DEV_NULL) ? path : entry.getOldPath(),
                kind, added, removed, binary);
    }

    private static FileDiff diffOf(
            final Repository repository,
            final DiffFormatter formatter,
            final ByteArrayOutputStream out,
            final List<DiffEntry> entries,
            final String path
    ) throws IOException {
        if (entries.isEmpty()) {
            return new FileDiff(path, path, false, false, List.of());
        }
        final DiffEntry entry = entries.get(0);
        final String oldPath = entry.getOldPath().equals(DiffEntry.DEV_NULL) ? path : entry.getOldPath();
        if (tooLarge(repository, entry)) {
            return new FileDiff(path, oldPath, false, true, List.of());
        }
        final FileHeader header = formatter.toFileHeader(entry);
        if (header.getPatchType() == FileHeader.PatchType.BINARY) {
            return new FileDiff(path, oldPath, true, false, List.of());
        }
        formatter.format(entry);
        final List<DiffHunk> hunks = UnifiedDiffParser.parse(out.toString(StandardCharsets.UTF_8));
        return new FileDiff(path, oldPath, false, false, hunks);
    }

    private static boolean tooLarge(
            final Repository repository,
            final DiffEntry entry
    ) throws IOException {
        for (final org.eclipse.jgit.diff.DiffEntry.Side side : org.eclipse.jgit.diff.DiffEntry.Side.values()) {
            final org.eclipse.jgit.lib.AbbreviatedObjectId abbreviated = entry.getId(side);
            if (abbreviated == null || !abbreviated.isComplete()) {
                continue;
            }
            final ObjectId id = abbreviated.toObjectId();
            if (!ObjectId.zeroId().equals(id) && repository.getObjectDatabase().has(id)
                    && repository.open(id).getSize() > MAX_DIFF_BYTES) {
                return true;
            }
        }
        return false;
    }

    /** Eine neue, noch nicht verfolgte Datei: alle Zeilen als hinzugefügt. */
    private static FileDiff newFileDiff(
            final File file,
            final String path
    ) throws IOException {
        if (!file.isFile()) {
            return new FileDiff(path, path, false, false, List.of());
        }
        if (file.length() > MAX_DIFF_BYTES) {
            return new FileDiff(path, path, false, true, List.of());
        }
        final byte[] bytes = Files.readAllBytes(file.toPath());
        for (final byte value : bytes) {
            if (value == 0) {
                return new FileDiff(path, path, true, false, List.of());
            }
        }
        final String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.isEmpty()) {
            return new FileDiff(path, path, false, false, List.of());
        }
        final String[] split = text.split("\n", -1);
        final int count = text.endsWith("\n") ? split.length - 1 : split.length;
        final List<DiffLine> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index += 1) {
            lines.add(new DiffLine(DiffLine.Type.ADDED, 0, index + 1, split[index]));
        }
        return new FileDiff(path, path, false, false, List.of(new DiffHunk("@@ -0,0 +1," + count + " @@", lines)));
    }

    private static CommitInfo infoOf(
            final RevCommit commit,
            final Map<ObjectId, List<RefLabel>> labels
    ) {
        final org.eclipse.jgit.lib.PersonIdent author = commit.getAuthorIdent();
        final List<String> parents = new ArrayList<>(commit.getParentCount());
        for (final RevCommit parent : commit.getParents()) {
            parents.add(parent.getName());
        }
        return new CommitInfo(commit.getName(), commit.getShortMessage(), commit.getFullMessage().strip(),
                author.getName(), author.getEmailAddress(), author.getWhen().getTime(), parents,
                labels.getOrDefault(commit.copy(), List.of()));
    }

    /** Alle Marken, nach Commit gruppiert; HEAD zuerst, dann Branches, Remote-Branches, Tags. */
    static Map<ObjectId, List<RefLabel>> labelsOf(
            final Repository repository
    ) throws IOException {
        final Map<ObjectId, List<RefLabel>> labels = new HashMap<>();
        final ObjectId head = repository.resolve(Constants.HEAD);
        if (head != null) {
            labels.computeIfAbsent(head, key -> new ArrayList<>()).add(new RefLabel(Constants.HEAD, RefLabel.Kind.HEAD));
        }
        for (final Ref ref : repository.getRefDatabase().getRefs()) {
            final String name = ref.getName();
            final RefLabel.Kind kind;
            if (name.startsWith(Constants.R_HEADS)) {
                kind = RefLabel.Kind.BRANCH;
            } else if (name.startsWith(Constants.R_REMOTES) && !name.endsWith("/" + Constants.HEAD)) {
                kind = RefLabel.Kind.REMOTE_BRANCH;
            } else if (name.startsWith(Constants.R_TAGS)) {
                kind = RefLabel.Kind.TAG;
            } else {
                continue;
            }
            final ObjectId target = peeled(repository, ref);
            if (target != null) {
                labels.computeIfAbsent(target, key -> new ArrayList<>())
                        .add(new RefLabel(Repository.shortenRefName(name), kind));
            }
        }
        for (final List<RefLabel> list : labels.values()) {
            list.sort(Comparator.comparing((RefLabel label) -> label.kind().ordinal()).thenComparing(RefLabel::name));
        }
        return labels;
    }

    private static ObjectId peeled(
            final Repository repository,
            final Ref ref
    ) throws IOException {
        final Ref peeled = repository.getRefDatabase().peel(ref);
        return peeled.getPeeledObjectId() != null ? peeled.getPeeledObjectId() : peeled.getObjectId();
    }

    /** Behält Commits, deren Autor und Nachricht die Suchtexte enthalten (ohne Beachtung der Groß-/Kleinschreibung). */
    private static final class ContainsFilter extends RevFilter {

        private final String author;
        private final String text;

        private ContainsFilter(
                final String author,
                final String text
        ) {
            this.author = author.toLowerCase(Locale.ROOT);
            this.text = text.toLowerCase(Locale.ROOT);
        }

        @Override
        public boolean include(
                final RevWalk walker,
                final RevCommit commit
        ) {
            if (!author.isEmpty()) {
                final org.eclipse.jgit.lib.PersonIdent ident = commit.getAuthorIdent();
                final String who = (ident.getName() + " " + ident.getEmailAddress()).toLowerCase(Locale.ROOT);
                if (!who.contains(author)) {
                    return false;
                }
            }
            return text.isEmpty() || commit.getFullMessage().toLowerCase(Locale.ROOT).contains(text);
        }

        @Override
        public RevFilter clone() {
            return new ContainsFilter(author, text);
        }

        @Override
        public boolean requiresCommitBody() {
            return true;
        }
    }
}
