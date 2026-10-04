package de.lembergmax.gitmax.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.RepoNames.Problem;

import org.junit.Test;

import java.util.Optional;

/** Prüft, welche Namen für neue Repos durchgehen. */
public final class RepoNamesTest {

    @Test
    public void ordinaryNamesAreAccepted() {
        assertTrue(RepoNames.check("mein-projekt").isEmpty());
        assertTrue(RepoNames.check("Projekt_2.0").isEmpty());
        assertTrue(RepoNames.check("a").isEmpty());
        assertTrue(RepoNames.check("x".repeat(100)).isEmpty());
    }

    @Test
    public void emptyOrBlankNamesAreRefused() {
        assertEquals(Optional.of(Problem.EMPTY), RepoNames.check(""));
        assertEquals(Optional.of(Problem.EMPTY), RepoNames.check("   "));
        assertEquals(Optional.of(Problem.EMPTY), RepoNames.check(null));
    }

    @Test
    public void tooLongNamesAreRefused() {
        assertEquals(Optional.of(Problem.TOO_LONG), RepoNames.check("x".repeat(101)));
    }

    @Test
    public void spacesSlashesAndSpecialCharactersAreRefused() {
        assertEquals(Optional.of(Problem.INVALID_CHARACTERS), RepoNames.check("mein projekt"));
        assertEquals(Optional.of(Problem.INVALID_CHARACTERS), RepoNames.check("a/b"));
        assertEquals(Optional.of(Problem.INVALID_CHARACTERS), RepoNames.check("ümlaut"));
        assertEquals(Optional.of(Problem.INVALID_CHARACTERS), RepoNames.check("a:b"));
        assertEquals(Optional.of(Problem.INVALID_CHARACTERS), RepoNames.check("a\\b"));
    }

    @Test
    public void reservedNamesAreRefused() {
        assertEquals(Optional.of(Problem.RESERVED), RepoNames.check("."));
        assertEquals(Optional.of(Problem.RESERVED), RepoNames.check(".."));
        assertEquals(Optional.of(Problem.RESERVED), RepoNames.check("repo.git"));
        assertEquals(Optional.of(Problem.RESERVED), RepoNames.check("REPO.GIT"));
        assertEquals(Optional.of(Problem.RESERVED), RepoNames.check(".versteckt"));
    }
}
