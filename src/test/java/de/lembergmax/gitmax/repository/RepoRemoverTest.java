package de.lembergmax.gitmax.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.UpdateRequest;
import de.lembergmax.gitmax.storage.MemoryKeyValueStore;
import de.lembergmax.gitmax.storage.RepoSettingsStore;
import de.lembergmax.gitmax.storage.WorkspaceStore;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class RepoRemoverTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final MemoryKeyValueStore store = new MemoryKeyValueStore();
    private WorkspaceStore workspace;
    private RepoSettingsStore settings;
    private RepoRemover remover;
    private File root;

    @Before
    public void setUp() throws IOException {
        root = folder.newFolder("GitMax");
        workspace = new WorkspaceStore(store);
        settings = new RepoSettingsStore(store);
        workspace.addRoot(root);
        remover = new RepoRemover(workspace, settings);
    }

    @Test
    public void deletesTheRepoWithAllItsContent() throws Exception {
        final File repo = repo("alpha");
        Files.write(new File(repo, "sub/datei.txt").toPath(), new byte[] {1, 2, 3});

        remover.delete(repo);

        assertFalse(repo.exists());
        assertTrue(root.isDirectory());
    }

    @Test
    public void leavesOtherReposAlone() throws Exception {
        final File first = repo("alpha");
        final File second = repo("beta");

        remover.delete(first);

        assertFalse(first.exists());
        assertTrue(new File(second, ".git").isDirectory());
    }

    @Test
    public void forgetsTheSettingsOfTheRepo() throws Exception {
        final File repo = repo("alpha");
        settings.setIdentity(repo, new CommitIdentity("Max", "max@example.invalid"));
        settings.setUpdateStrategy(repo, UpdateRequest.Strategy.REBASE);

        remover.delete(repo);

        assertTrue(settings.identity(repo).isEmpty());
        assertEquals(UpdateRequest.Strategy.MERGE, settings.updateStrategy(repo));
    }

    @Test
    public void refusesAFolderThatIsNoRepo() throws Exception {
        final File plain = folder.newFolder("GitMax", "dokumente");
        Files.write(new File(plain, "wichtig.txt").toPath(), new byte[] {1});

        final RepoRemover.RemovalException failure = assertThrows(RepoRemover.RemovalException.class, () -> remover.delete(plain));

        assertEquals(RepoRemover.Reason.NOT_A_REPO, failure.reason());
        assertTrue(new File(plain, "wichtig.txt").isFile());
    }

    @Test
    public void neverDeletesAWorkspaceRootEvenIfItIsARepo() throws Exception {
        assertTrue(new File(root, ".git").mkdir());
        final File inside = repo("alpha");

        final RepoRemover.RemovalException failure = assertThrows(RepoRemover.RemovalException.class, () -> remover.delete(root));

        assertEquals(RepoRemover.Reason.WORKSPACE_ROOT, failure.reason());
        assertTrue(inside.isDirectory());
    }

    private File repo(
            final String name
    ) throws IOException {
        final File repo = folder.newFolder("GitMax", name);
        assertTrue(new File(repo, ".git").mkdir());
        Files.write(new File(repo, ".git/HEAD").toPath(), "ref: refs/heads/main\n".getBytes());
        assertTrue(new File(repo, "sub").mkdir());
        return repo;
    }
}
