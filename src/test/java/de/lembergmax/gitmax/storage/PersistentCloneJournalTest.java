package de.lembergmax.gitmax.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/** Prüft, dass nur Ordner halber Klone beim Start verschwinden und nie ein fertiger Klon. */
public final class PersistentCloneJournalTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final MemoryKeyValueStore store = new MemoryKeyValueStore();

    @Test
    public void theFolderOfAnInterruptedCloneIsDeletedWithItsContent() throws Exception {
        final File half = cloneFolder("halb");
        new PersistentCloneJournal(store).begin(half);

        // Ein neuer Prozess sieht nur noch das, was im Speicher steht.
        final List<File> removed = new PersistentCloneJournal(store).cleanUpInterrupted();

        assertEquals(List.of(half), removed);
        assertFalse(half.exists());
    }

    @Test
    public void aFinishedCloneStaysWhenTheAppStartsAgain() throws Exception {
        final File done = cloneFolder("fertig");
        final PersistentCloneJournal journal = new PersistentCloneJournal(store);
        journal.begin(done);
        journal.end(done);

        final List<File> removed = new PersistentCloneJournal(store).cleanUpInterrupted();

        assertTrue(removed.isEmpty());
        assertTrue(done.isDirectory());
    }

    @Test
    public void onlyTheUnfinishedOneOfSeveralClonesIsDeleted() throws Exception {
        final File done = cloneFolder("fertig");
        final File half = cloneFolder("halb");
        final PersistentCloneJournal journal = new PersistentCloneJournal(store);
        journal.begin(done);
        journal.begin(half);
        journal.end(done);

        new PersistentCloneJournal(store).cleanUpInterrupted();

        assertTrue(done.isDirectory());
        assertFalse(half.exists());
    }

    @Test
    public void aFolderThatIsAlreadyGoneIsNoProblem() throws Exception {
        final File gone = new File(folder.getRoot(), "weg");
        new PersistentCloneJournal(store).begin(gone);

        new PersistentCloneJournal(store).cleanUpInterrupted();

        assertFalse(gone.exists());
    }

    @Test
    public void theJournalIsEmptyAfterTheCleanUp() throws Exception {
        final File half = cloneFolder("halb");
        new PersistentCloneJournal(store).begin(half);
        final PersistentCloneJournal restarted = new PersistentCloneJournal(store);
        restarted.cleanUpInterrupted();
        assertTrue(half.mkdirs());

        final List<File> second = restarted.cleanUpInterrupted();

        assertTrue("Ein zweiter Lauf darf einen neu angelegten Ordner nicht anfassen", second.isEmpty());
        assertTrue(half.isDirectory());
    }

    private File cloneFolder(
            final String name
    ) throws Exception {
        final File target = folder.newFolder(name);
        Files.write(new File(target, "datei.txt").toPath(), new byte[] {1, 2, 3});
        return target;
    }
}
