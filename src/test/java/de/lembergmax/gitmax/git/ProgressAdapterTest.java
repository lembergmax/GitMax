package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ProgressAdapterTest {

    @Test
    public void transferTitlesMapToTheirPhases() {
        assertEquals(GitProgress.Phase.RECEIVING, ProgressAdapter.phaseFor("Receiving objects"));
        assertEquals(GitProgress.Phase.RECEIVING, ProgressAdapter.phaseFor("remote: Counting objects"));
        assertEquals(GitProgress.Phase.RESOLVING, ProgressAdapter.phaseFor("Resolving deltas"));
        assertEquals(GitProgress.Phase.PUSHING, ProgressAdapter.phaseFor("Writing objects"));
        assertEquals(GitProgress.Phase.PUSHING, ProgressAdapter.phaseFor("Compressing objects"));
    }

    @Test
    public void checkoutAndReferenceUpdatesAreDistinguished() {
        assertEquals(GitProgress.Phase.CHECKOUT, ProgressAdapter.phaseFor("Checking out files"));
        assertEquals(GitProgress.Phase.CHECKOUT, ProgressAdapter.phaseFor("Updating workdir"));
        assertEquals(GitProgress.Phase.FETCHING, ProgressAdapter.phaseFor("Updating references"));
    }

    @Test
    public void unknownTitlesFallBackToWorking() {
        assertEquals(GitProgress.Phase.WORKING, ProgressAdapter.phaseFor("Etwas anderes"));
        assertEquals(GitProgress.Phase.WORKING, ProgressAdapter.phaseFor(null));
    }
}
