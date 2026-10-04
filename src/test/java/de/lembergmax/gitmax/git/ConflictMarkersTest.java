package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Prüft das Erkennen und Auflösen von Konfliktmarken in Text. */
public final class ConflictMarkersTest {

    private static final String ONE_BLOCK =
            "vorher\n<<<<<<< HEAD\nmeine\n=======\nihre\n>>>>>>> feature\nnachher\n";

    @Test
    public void plainTextHasNoMarkers() {
        assertFalse(ConflictMarkers.hasMarkers("eins\nzwei\n"));
        assertEquals(0, ConflictMarkers.blockCount(""));
    }

    @Test
    public void aCompleteBlockIsDetected() {
        assertTrue(ConflictMarkers.hasMarkers(ONE_BLOCK));
        assertEquals(1, ConflictMarkers.blockCount(ONE_BLOCK));
    }

    @Test
    public void anIncompleteBlockDoesNotCount() {
        assertFalse(ConflictMarkers.hasMarkers("<<<<<<< HEAD\nmeine\n=======\nihre\n"));
        assertFalse(ConflictMarkers.hasMarkers("=======\n>>>>>>> x\n"));
    }

    @Test
    public void severalBlocksAreCounted() {
        final String text = ONE_BLOCK + "mitte\n<<<<<<< HEAD\na\n=======\nb\n>>>>>>> feature\n";

        assertEquals(2, ConflictMarkers.blockCount(text));
    }

    @Test
    public void keepBothKeepsOursThenTheirsWithoutMarkers() {
        assertEquals("vorher\nmeine\nihre\nnachher\n", ConflictMarkers.keepBoth(ONE_BLOCK));
    }

    @Test
    public void keepBothResolvesEveryBlock() {
        final String text = ONE_BLOCK + "mitte\n<<<<<<< HEAD\na\n=======\nb\n>>>>>>> feature\n";

        assertEquals("vorher\nmeine\nihre\nnachher\nmitte\na\nb\n", ConflictMarkers.keepBoth(text));
    }

    @Test
    public void keepBothDropsTheCommonAncestorOfDiff3Blocks() {
        final String text = "<<<<<<< HEAD\nmeine\n||||||| gemeinsam\nalt\n=======\nihre\n>>>>>>> feature\n";

        assertEquals("meine\nihre\n", ConflictMarkers.keepBoth(text));
    }

    @Test
    public void keepBothKeepsCrlfLineEndings() {
        final String text = "vorher\r\n<<<<<<< HEAD\r\nmeine\r\n=======\r\nihre\r\n>>>>>>> feature\r\nnachher\r\n";

        assertTrue(ConflictMarkers.hasMarkers(text));
        assertEquals("vorher\r\nmeine\r\nihre\r\nnachher\r\n", ConflictMarkers.keepBoth(text));
    }

    @Test
    public void keepBothKeepsAMissingFinalNewline() {
        final String text = "<<<<<<< HEAD\nmeine\n=======\nihre\n>>>>>>> feature";

        assertEquals("meine\nihre\n", ConflictMarkers.keepBoth(text));
    }

    @Test
    public void textWithoutBlocksStaysUnchanged() {
        final String text = "Titel\n=======\nText\n";

        assertFalse(ConflictMarkers.hasMarkers(text));
        assertEquals(text, ConflictMarkers.keepBoth(text));
    }

    @Test
    public void aSeparatorLookalikeInsideAnotherLineIsNotAMarker() {
        final String text = "<<<<<<< HEAD\nmeine\n======= nicht allein\n=======\nihre\n>>>>>>> feature\n";

        assertEquals("meine\n======= nicht allein\nihre\n", ConflictMarkers.keepBoth(text));
    }
}
