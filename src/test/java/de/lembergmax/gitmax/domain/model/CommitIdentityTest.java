package de.lembergmax.gitmax.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CommitIdentityTest {

    @Test
    public void trimsNameAndEmail() {
        final CommitIdentity identity = new CommitIdentity("  Max Lemberg ", " max@example.org ");

        assertEquals("Max Lemberg", identity.name());
        assertEquals("max@example.org", identity.email());
    }

    @Test
    public void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new CommitIdentity("   ", "max@example.org"));
    }

    @Test
    public void rejectsImplausibleEmail() {
        assertThrows(IllegalArgumentException.class, () -> new CommitIdentity("Max", "max"));
        assertThrows(IllegalArgumentException.class, () -> new CommitIdentity("Max", "max@"));
        assertThrows(IllegalArgumentException.class, () -> new CommitIdentity("Max", "@example.org"));
        assertThrows(IllegalArgumentException.class, () -> new CommitIdentity("Max", "a@b@c.de"));
        assertThrows(IllegalArgumentException.class, () -> new CommitIdentity("Max", "max @example.org"));
    }

    @Test
    public void plausibleEmailNeedsADotAfterTheAt() {
        assertTrue(CommitIdentity.isPlausibleEmail("max@example.org"));
        assertTrue(CommitIdentity.isPlausibleEmail("42+max@users.noreply.github.com"));
        assertFalse(CommitIdentity.isPlausibleEmail("max@localhost"));
        assertFalse(CommitIdentity.isPlausibleEmail(null));
    }
}
