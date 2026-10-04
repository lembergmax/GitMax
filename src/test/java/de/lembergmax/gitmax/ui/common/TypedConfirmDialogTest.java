package de.lembergmax.gitmax.ui.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TypedConfirmDialogTest {

    @Test
    public void theExactNameConfirms() {
        assertTrue(TypedConfirmDialog.matches("alpha", "alpha"));
    }

    @Test
    public void caseAndSurroundingSpacesDoNotMatter() {
        assertTrue(TypedConfirmDialog.matches("  Alpha ", "alpha"));
        assertTrue(TypedConfirmDialog.matches("ALPHA", "Alpha"));
    }

    @Test
    public void aPartOfTheNameOrNothingDoesNotConfirm() {
        assertFalse(TypedConfirmDialog.matches("alph", "alpha"));
        assertFalse(TypedConfirmDialog.matches("alpha2", "alpha"));
        assertFalse(TypedConfirmDialog.matches("", "alpha"));
    }
}
