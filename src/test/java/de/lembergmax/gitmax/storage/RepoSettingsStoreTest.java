package de.lembergmax.gitmax.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.UpdateRequest;

import org.junit.Test;

import java.io.File;

/** Prüft die Einstellungen je Repo. */
public final class RepoSettingsStoreTest {

    private final MemoryKeyValueStore backing = new MemoryKeyValueStore();
    private final RepoSettingsStore settings = new RepoSettingsStore(backing);
    private final File repo = new File("/sdcard/GitMax/alpha");

    @Test
    public void theStrategyDefaultsToMerge() {
        assertEquals(UpdateRequest.Strategy.MERGE, settings.updateStrategy(repo));
    }

    @Test
    public void aChosenStrategyIsRememberedPerRepo() {
        settings.setUpdateStrategy(repo, UpdateRequest.Strategy.REBASE);

        assertEquals(UpdateRequest.Strategy.REBASE, settings.updateStrategy(repo));
        assertEquals(UpdateRequest.Strategy.MERGE, settings.updateStrategy(new File("/sdcard/GitMax/beta")));
    }

    @Test
    public void anUnknownStoredStrategyFallsBackToMerge() {
        backing.put("repo.strategy." + repo.getAbsolutePath(), "GIBT_ES_NICHT");

        assertEquals(UpdateRequest.Strategy.MERGE, settings.updateStrategy(repo));
    }

    @Test
    public void strategyAndIdentityDoNotInterfere() {
        settings.setIdentity(repo, new CommitIdentity("Max", "max@example.invalid"));
        settings.setUpdateStrategy(repo, UpdateRequest.Strategy.FAST_FORWARD_ONLY);

        assertEquals("Max", settings.identity(repo).orElseThrow().name());
        assertEquals(UpdateRequest.Strategy.FAST_FORWARD_ONLY, settings.updateStrategy(repo));
        assertTrue(settings.identity(new File("/anderes")).isEmpty());
    }
}
