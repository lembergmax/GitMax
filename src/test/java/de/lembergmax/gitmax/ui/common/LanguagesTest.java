package de.lembergmax.gitmax.ui.common;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class LanguagesTest {

    @Test
    public void knownLanguagesMapToTheirChoice() {
        assertEquals(Languages.Choice.ENGLISH, Languages.choiceFor("en"));
        assertEquals(Languages.Choice.GERMAN, Languages.choiceFor("de"));
        assertEquals(Languages.Choice.GERMAN, Languages.choiceFor("DE"));
    }

    @Test
    public void unknownLanguagesFollowTheSystem() {
        assertEquals(Languages.Choice.SYSTEM, Languages.choiceFor("fr"));
        assertEquals(Languages.Choice.SYSTEM, Languages.choiceFor(""));
    }

    @Test
    public void everyChoiceHasADistinctTag() {
        assertEquals("", Languages.Choice.SYSTEM.tag());
        assertEquals("en", Languages.Choice.ENGLISH.tag());
        assertEquals("de", Languages.Choice.GERMAN.tag());
    }
}
