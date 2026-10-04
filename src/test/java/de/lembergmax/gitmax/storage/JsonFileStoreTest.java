package de.lembergmax.gitmax.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Optional;

public final class JsonFileStoreTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private File directory;
    private JsonFileStore store;

    @Before
    public void setUp() {
        directory = new File(folder.getRoot(), "cache");
        store = new JsonFileStore(directory);
    }

    @Test
    public void writesAndReadsAndCreatesTheDirectory() throws Exception {
        store.write("liste.json", "{\"a\":1}");

        assertEquals(Optional.of("{\"a\":1}"), store.read("liste.json"));
        assertTrue(directory.isDirectory());
    }

    @Test
    public void replacingLeavesNoTempFile() throws Exception {
        store.write("liste.json", "alt");
        store.write("liste.json", "neu");

        assertEquals(Optional.of("neu"), store.read("liste.json"));
        assertEquals(1, directory.list().length);
    }

    @Test
    public void missingFileIsEmpty() {
        assertEquals(Optional.empty(), store.read("nichts.json"));
    }

    @Test
    public void deleteRemovesTheFileAndToleratesMissingOnes() throws Exception {
        store.write("liste.json", "x");
        store.delete("liste.json");

        assertFalse(new File(directory, "liste.json").exists());
        store.delete("liste.json");
    }

    @Test
    public void keepsUmlautsAndEmojiIntact() throws Exception {
        store.write("liste.json", "Übung ä ö ü ß 😀");

        assertEquals(Optional.of("Übung ä ö ü ß 😀"), store.read("liste.json"));
    }

    @Test
    public void rejectsNamesThatCouldEscapeTheDirectory() {
        assertThrows(IllegalArgumentException.class, () -> store.write("../boese.json", "x"));
        assertThrows(IllegalArgumentException.class, () -> store.write("a/b.json", "x"));
        assertThrows(IllegalArgumentException.class, () -> store.read(".."));
        assertThrows(IllegalArgumentException.class, () -> store.delete(""));
    }
}
