package de.lembergmax.gitmax.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.ChangedFile;
import de.lembergmax.gitmax.domain.model.WorkingTree;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

public final class FileStatusIndexTest {

    private static final ChangedFile.Kind MODIFIED = ChangedFile.Kind.MODIFIED;

    @Test
    public void reportsTheKindOfChangedFilesOnly() {
        final FileStatusIndex index = FileStatusIndex.of(tree(
                List.of(), List.of(file("a.txt", ChangedFile.Kind.ADDED)),
                List.of(file("src/b.txt", MODIFIED)), List.of(file("neu.txt", ChangedFile.Kind.UNTRACKED))));

        assertEquals(Optional.of(ChangedFile.Kind.ADDED), index.kindOf("a.txt"));
        assertEquals(Optional.of(MODIFIED), index.kindOf("src/b.txt"));
        assertEquals(Optional.of(ChangedFile.Kind.UNTRACKED), index.kindOf("neu.txt"));
        assertTrue(index.kindOf("unveraendert.txt").isEmpty());
    }

    @Test
    public void aConflictBeatsEveryOtherEntryForTheSameFile() {
        final FileStatusIndex index = FileStatusIndex.of(tree(
                List.of(file("x.txt", ChangedFile.Kind.CONFLICT)), List.of(file("x.txt", MODIFIED)),
                List.of(file("x.txt", MODIFIED)), List.of()));

        assertEquals(Optional.of(ChangedFile.Kind.CONFLICT), index.kindOf("x.txt"));
    }

    @Test
    public void aFolderCountsAsChangedWhenAnythingBelowItIsChanged() {
        final FileStatusIndex index = FileStatusIndex.of(tree(
                List.of(), List.of(), List.of(file("src/util/Helper.java", MODIFIED)), List.of()));

        assertTrue(index.hasChangesIn("src"));
        assertTrue(index.hasChangesIn("src/util"));
        assertTrue(index.hasChangesIn(""));
        assertFalse(index.hasChangesIn("docs"));
    }

    @Test
    public void aFolderNameIsNotConfusedWithAFilePrefix() {
        final FileStatusIndex index = FileStatusIndex.of(tree(
                List.of(), List.of(), List.of(file("srcfile.txt", MODIFIED)), List.of()));

        assertFalse(index.hasChangesIn("src"));
    }

    private static ChangedFile file(
            final String path,
            final ChangedFile.Kind kind
    ) {
        return new ChangedFile(path, kind);
    }

    private static WorkingTree tree(
            final List<ChangedFile> conflicts,
            final List<ChangedFile> staged,
            final List<ChangedFile> unstaged,
            final List<ChangedFile> untracked
    ) {
        return new WorkingTree(conflicts, staged, unstaged, untracked, false);
    }
}
