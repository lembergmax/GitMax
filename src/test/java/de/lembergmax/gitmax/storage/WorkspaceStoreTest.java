package de.lembergmax.gitmax.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;

public final class WorkspaceStoreTest {

    private static final File A = new File("/speicher/projekte").getAbsoluteFile();
    private static final File B = new File("/speicher/arbeit").getAbsoluteFile();

    private MemoryKeyValueStore storage;
    private WorkspaceStore workspace;

    @Before
    public void setUp() {
        storage = new MemoryKeyValueStore();
        workspace = new WorkspaceStore(storage);
    }

    @Test
    public void startsEmpty() {
        assertTrue(workspace.roots().isEmpty());
        assertEquals(Optional.empty(), workspace.defaultTarget());
    }

    @Test
    public void firstRootBecomesTheDefaultTarget() {
        workspace.addRoot(A);
        workspace.addRoot(B);

        assertEquals(List.of(A, B), workspace.roots());
        assertEquals(Optional.of(A), workspace.defaultTarget());
    }

    @Test
    public void addingTheSameRootTwiceKeepsOne() {
        workspace.addRoot(A);
        workspace.addRoot(A);

        assertEquals(List.of(A), workspace.roots());
    }

    @Test
    public void defaultTargetMustBeARoot() {
        workspace.addRoot(A);

        assertThrows(IllegalArgumentException.class, () -> workspace.setDefaultTarget(B));
    }

    @Test
    public void defaultTargetCanBeChanged() {
        workspace.addRoot(A);
        workspace.addRoot(B);

        workspace.setDefaultTarget(B);

        assertEquals(Optional.of(B), workspace.defaultTarget());
    }

    @Test
    public void removingTheDefaultPromotesTheNextRoot() {
        workspace.addRoot(A);
        workspace.addRoot(B);

        workspace.removeRoot(A);

        assertEquals(List.of(B), workspace.roots());
        assertEquals(Optional.of(B), workspace.defaultTarget());
    }

    @Test
    public void removingTheLastRootClearsTheDefault() {
        workspace.addRoot(A);

        workspace.removeRoot(A);

        assertTrue(workspace.roots().isEmpty());
        assertEquals(Optional.empty(), workspace.defaultTarget());
    }

    @Test
    public void removingAnUnknownRootChangesNothing() {
        workspace.addRoot(A);

        workspace.removeRoot(B);

        assertEquals(List.of(A), workspace.roots());
    }

    @Test
    public void damagedStorageGivesNoRoots() {
        storage.raw().put("workspace.roots.v1", "{kaputt");

        assertTrue(workspace.roots().isEmpty());
    }
}
