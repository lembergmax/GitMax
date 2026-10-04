package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.RemoteUrl;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

public final class RepoOriginsTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readsTheOriginAddress() throws Exception {
        final File repo = repoWithConfig("[remote \"origin\"]\n\turl = https://github.com/max/alpha.git\n");

        final Optional<RemoteUrl> origin = RepoOrigins.originOf(repo);

        assertEquals("max/alpha", origin.orElseThrow().displayName());
    }

    @Test
    public void fallsBackToTheFirstRemoteWhenThereIsNoOrigin() throws Exception {
        final File repo = repoWithConfig(
                "[remote \"zweit\"]\n\turl = https://gitlab.com/g/z.git\n[remote \"erst\"]\n\turl = https://github.com/max/e.git\n");

        assertEquals("max/e", RepoOrigins.originOf(repo).orElseThrow().displayName());
    }

    @Test
    public void aRepoWithoutRemoteHasNoOrigin() throws Exception {
        assertTrue(RepoOrigins.originOf(repoWithConfig("[core]\n\tbare = false\n")).isEmpty());
    }

    @Test
    public void aFolderThatIsNoRepoHasNoOrigin() throws Exception {
        assertTrue(RepoOrigins.originOf(folder.newFolder("leer")).isEmpty());
    }

    @Test
    public void anUnreadableAddressIsIgnored() throws Exception {
        assertTrue(RepoOrigins.originOf(repoWithConfig("[remote \"origin\"]\n\turl = /lokaler/pfad\n")).isEmpty());
    }

    private File repoWithConfig(
            final String config
    ) throws Exception {
        final File repo = folder.newFolder();
        final File git = new File(repo, ".git");
        assertTrue(git.mkdirs());
        Files.write(new File(git, "config").toPath(), config.getBytes(StandardCharsets.UTF_8));
        return repo;
    }
}
