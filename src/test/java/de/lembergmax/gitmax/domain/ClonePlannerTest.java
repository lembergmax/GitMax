package de.lembergmax.gitmax.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.RepoNaming.FolderState;

import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ClonePlannerTest {

    private static final File ROOT = new File("/storage/emulated/0/GitMax");

    /** Ordnerinhalt im Test: Name (klein) → Zustand; alles andere fehlt. */
    private final Map<String, FolderState> folders = new HashMap<>();

    private final ClonePlanner.FolderInspector inspector = (folder, url) ->
            folders.getOrDefault(folder.getName().toLowerCase(Locale.ROOT), FolderState.MISSING);

    @Test
    public void everyRepoGetsItsOwnFolderInTheTargetFolder() {
        final List<ClonePlanner.Plan> plans = ClonePlanner.plan(
                List.of(url("max/alpha"), url("max/beta")), ROOT, inspector);

        assertEquals(new File(ROOT, "alpha"), plans.get(0).target());
        assertEquals(new File(ROOT, "beta"), plans.get(1).target());
        assertFalse(plans.get(0).alreadyCloned());
    }

    @Test
    public void aFolderOfSomethingElseMakesThePlanUseTheOwnerName() {
        folders.put("app", FolderState.OTHER);

        final ClonePlanner.Plan plan = ClonePlanner.plan(List.of(url("firma/app")), ROOT, inspector).get(0);

        assertEquals("app-firma", plan.folderName());
    }

    @Test
    public void theSameRepoOnDiskIsRecognisedInsteadOfBeingClonedAgain() {
        folders.put("alpha", FolderState.SAME_REPO);

        final ClonePlanner.Plan plan = ClonePlanner.plan(List.of(url("max/alpha")), ROOT, inspector).get(0);

        assertTrue(plan.alreadyCloned());
        assertEquals("alpha", plan.folderName());
    }

    @Test
    public void twoReposOfTheSameBatchWithTheSameNameDoNotShareAFolder() {
        final List<ClonePlanner.Plan> plans = ClonePlanner.plan(
                List.of(url("max/app"), url("firma/app"), url("team/app")), ROOT, inspector);

        assertEquals("app", plans.get(0).folderName());
        assertEquals("app-firma", plans.get(1).folderName());
        assertEquals("app-team", plans.get(2).folderName());
    }

    @Test
    public void folderNamesAreComparedWithoutRegardToCase() {
        final List<ClonePlanner.Plan> plans = ClonePlanner.plan(
                List.of(url("max/App"), url("firma/app")), ROOT, inspector);

        assertEquals("App", plans.get(0).folderName());
        assertEquals("app-firma", plans.get(1).folderName());
    }

    @Test
    public void theSameRepoTwiceCountsOnce() {
        final List<ClonePlanner.Plan> plans = ClonePlanner.plan(
                List.of(url("max/alpha"), RemoteUrl.parse("git@github.com:max/alpha.git").orElseThrow()), ROOT, inspector);

        assertEquals(1, plans.size());
    }

    @Test
    public void forbiddenCharactersInTheRepoNameAreReplaced() {
        final ClonePlanner.Plan plan = ClonePlanner.plan(List.of(url("max/a:b")), ROOT, inspector).get(0);

        assertEquals("a_b", plan.folderName());
    }

    private static RemoteUrl url(
            final String path
    ) {
        return RemoteUrl.parse("https://github.com/" + path + ".git").orElseThrow();
    }
}
