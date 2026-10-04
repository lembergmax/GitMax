package de.lembergmax.gitmax.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FolderNameTest {

    @Test
    public void ordinaryNamesAreValid() {
        assertTrue(FolderName.isValid("GitMax"));
        assertTrue(FolderName.isValid("Meine Projekte 2026"));
        assertTrue(FolderName.isValid("Übung-ß"));
        assertTrue(FolderName.isValid("repo.v2"));
        assertTrue(FolderName.isValid("  mit-rand  "));
    }

    @Test
    public void blankAndDotNamesAreInvalid() {
        assertFalse(FolderName.isValid(""));
        assertFalse(FolderName.isValid("   "));
        assertFalse(FolderName.isValid("."));
        assertFalse(FolderName.isValid(".."));
    }

    @Test
    public void everyCharacterThePhoneStorageRejectsIsInvalid() {
        for (final char forbidden : "/\\:*?\"<>|".toCharArray()) {
            assertFalse("Zeichen " + forbidden, FolderName.isValid("a" + forbidden + "b"));
        }
    }

    @Test
    public void controlCharactersAreInvalid() {
        assertFalse(FolderName.isValid("a\nb"));
        assertFalse(FolderName.isValid("a\u0000b"));
    }
}
