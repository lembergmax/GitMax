package de.lembergmax.gitmax.domain.syntax;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Prüft die Zuordnung von Dateinamen zu Sprachen. */
public final class SyntaxLanguageTest {

    @Test
    public void extensionsMapToLanguages() {
        assertEquals(SyntaxLanguage.JAVA_LIKE, SyntaxLanguage.forFileName("Main.java"));
        assertEquals(SyntaxLanguage.JAVA_LIKE, SyntaxLanguage.forFileName("build.gradle.kts"));
        assertEquals(SyntaxLanguage.SCRIPT, SyntaxLanguage.forFileName("app.tsx"));
        assertEquals(SyntaxLanguage.C_LIKE, SyntaxLanguage.forFileName("lib.rs"));
        assertEquals(SyntaxLanguage.PYTHON, SyntaxLanguage.forFileName("skript.py"));
        assertEquals(SyntaxLanguage.JSON, SyntaxLanguage.forFileName("package.json"));
        assertEquals(SyntaxLanguage.YAML, SyntaxLanguage.forFileName("ci.yml"));
        assertEquals(SyntaxLanguage.XML, SyntaxLanguage.forFileName("AndroidManifest.xml"));
        assertEquals(SyntaxLanguage.MARKDOWN, SyntaxLanguage.forFileName("README.md"));
    }

    @Test
    public void extensionsIgnoreCase() {
        assertEquals(SyntaxLanguage.JAVA_LIKE, SyntaxLanguage.forFileName("MAIN.JAVA"));
        assertEquals(SyntaxLanguage.XML, SyntaxLanguage.forFileName("Seite.HTML"));
    }

    @Test
    public void specialFileNamesWork() {
        assertEquals(SyntaxLanguage.HASH_COMMENTS, SyntaxLanguage.forFileName(".gitignore"));
        assertEquals(SyntaxLanguage.SHELL, SyntaxLanguage.forFileName("Dockerfile"));
        assertEquals(SyntaxLanguage.PROPERTIES, SyntaxLanguage.forFileName(".editorconfig"));
    }

    @Test
    public void unknownNamesAreNotColored() {
        assertEquals(SyntaxLanguage.PLAIN, SyntaxLanguage.forFileName("LICENSE"));
        assertEquals(SyntaxLanguage.PLAIN, SyntaxLanguage.forFileName("daten.xyz"));
        assertEquals(SyntaxLanguage.PLAIN, SyntaxLanguage.forFileName("ende."));
        assertEquals(SyntaxLanguage.PLAIN, SyntaxLanguage.forFileName(""));
    }
}
