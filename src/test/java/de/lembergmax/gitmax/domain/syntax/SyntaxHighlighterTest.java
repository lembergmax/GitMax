package de.lembergmax.gitmax.domain.syntax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.syntax.SyntaxToken.Type;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Prüft die leichte Syntaxfärbung je Sprache und die Invarianten für beliebigen Text. */
public final class SyntaxHighlighterTest {

    /** Die Abschnitte als {@code ART:Text}, damit Erwartungen lesbar bleiben. */
    private static List<String> show(
            final String text,
            final SyntaxLanguage language
    ) {
        final List<String> shown = new ArrayList<>();
        for (final SyntaxToken token : SyntaxHighlighter.highlight(text, language)) {
            shown.add(token.type() + ":" + text.substring(token.start(), token.end()));
        }
        return shown;
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Code                                                                                       */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void javaKeywordsStringsNumbersAndComments() {
        final String code = "public int x = 42; // Zahl\nString s = \"a\\\"b\"; /* Block */";

        assertEquals(List.of("KEYWORD:public", "KEYWORD:int", "NUMBER:42", "COMMENT:// Zahl",
                "STRING:\"a\\\"b\"", "COMMENT:/* Block */"), show(code, SyntaxLanguage.JAVA_LIKE));
    }

    @Test
    public void javaLiteralsAndTextBlocks() {
        final String code = "boolean b = true;\nString t = \"\"\"\n  zeile \"mit\" anführungszeichen\n\"\"\";";

        final List<String> tokens = show(code, SyntaxLanguage.JAVA_LIKE);

        assertTrue(tokens.contains("LITERAL:true"));
        assertTrue(tokens.stream().anyMatch(token -> token.startsWith("STRING:\"\"\"") && token.contains("mit")));
    }

    @Test
    public void anUnterminatedStringEndsAtTheLineBreak() {
        final String code = "String a = \"offen;\nint b = 1;";

        final List<String> tokens = show(code, SyntaxLanguage.JAVA_LIKE);

        assertTrue(tokens.contains("STRING:\"offen;"));
        assertTrue("Die nächste Zeile bleibt unberührt", tokens.contains("NUMBER:1"));
        assertTrue(tokens.contains("KEYWORD:int"));
    }

    @Test
    public void anUnterminatedBlockCommentRunsToTheEnd() {
        final String code = "int a; /* nie zu\nint b;";

        final List<String> tokens = show(code, SyntaxLanguage.JAVA_LIKE);

        assertEquals("COMMENT:/* nie zu\nint b;", tokens.get(tokens.size() - 1));
    }

    @Test
    public void keywordsInsideWordsAreNotKeywords() {
        assertEquals(List.of(), show("integer printer classy", SyntaxLanguage.JAVA_LIKE));
    }

    @Test
    public void numbersCoverHexFloatsAndSuffixes() {
        assertEquals(List.of("NUMBER:0xFF", "NUMBER:1_000", "NUMBER:3.14f", "NUMBER:1e10", "NUMBER:.5"),
                show("0xFF 1_000 3.14f 1e10 .5", SyntaxLanguage.JAVA_LIKE));
    }

    @Test
    public void typescriptTemplateStringsMayRunOverSeveralLines() {
        final String code = "const t = `eins\nzwei`;";

        final List<String> tokens = show(code, SyntaxLanguage.SCRIPT);

        assertEquals(List.of("KEYWORD:const", "STRING:`eins\nzwei`"), tokens);
    }

    @Test
    public void rustLifetimesAreNotStrings() {
        final String code = "fn foo<'a>(x: &'a str) -> char { 'x' }";

        final List<String> tokens = show(code, SyntaxLanguage.C_LIKE);

        assertTrue(tokens.contains("KEYWORD:fn"));
        assertTrue(tokens.contains("STRING:'x'"));
        assertFalse(tokens.stream().anyMatch(token -> token.startsWith("STRING:'a")));
    }

    @Test
    public void pythonHashCommentsAndTripleQuotes() {
        final String code = "def f(x):  # Kommentar\n    \"\"\"Doku\n    mehrzeilig\"\"\"\n    return None";

        assertEquals(List.of("KEYWORD:def", "COMMENT:# Kommentar", "STRING:\"\"\"Doku\n    mehrzeilig\"\"\"",
                "KEYWORD:return", "LITERAL:None"), show(code, SyntaxLanguage.PYTHON));
    }

    @Test
    public void shellVariablesCommentsAndStrings() {
        final String code = "if [ -n \"$HOME\" ]; then echo ${PATH} $# # fertig\nfi";

        assertEquals(List.of("KEYWORD:if", "STRING:\"$HOME\"", "KEYWORD:then", "KEYWORD:echo", "ATTRIBUTE:${PATH}",
                "ATTRIBUTE:$#", "COMMENT:# fertig", "KEYWORD:fi"), show(code, SyntaxLanguage.SHELL));
    }

    @Test
    public void aHashInsideAShellWordIsNoComment() {
        assertEquals(List.of("KEYWORD:echo"), show("echo foo#bar", SyntaxLanguage.SHELL));
    }

    @Test
    public void sqlKeywordsIgnoreCase() {
        assertEquals(List.of("KEYWORD:SELECT", "KEYWORD:from", "STRING:'x'", "COMMENT:-- Ende"),
                show("SELECT a from t where 'x' -- Ende".replace("a ", "").replace("t where ", ""), SyntaxLanguage.SQL));
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Daten und Auszeichnung                                                                     */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void jsonKeysAreSeparateFromValues() {
        final String json = "{\"name\": \"GitMax\", \"zahl\": -12.5, \"ok\": true, \"nix\": null}";

        assertEquals(List.of("KEY:\"name\"", "STRING:\"GitMax\"", "KEY:\"zahl\"", "NUMBER:12.5", "KEY:\"ok\"",
                "LITERAL:true", "KEY:\"nix\"", "LITERAL:null"), show(json, SyntaxLanguage.JSON));
    }

    @Test
    public void yamlKeysCommentsAndValues() {
        final String yaml = "name: GitMax  # Name\nitems:\n  - eins: 1\n  - \"zwei\": true\nurl: http://x#frag\n";

        assertEquals(List.of("KEY:name", "COMMENT:# Name", "KEY:items", "KEY:eins", "NUMBER:1", "KEY:\"zwei\"",
                "LITERAL:true", "KEY:url"), show(yaml, SyntaxLanguage.YAML));
    }

    @Test
    public void xmlTagsAttributesAndComments() {
        final String xml = "<?xml version=\"1.0\"?>\n<!-- c --><a href=\"x\" b='y'>text</a><br/>";

        assertEquals(List.of("KEYWORD:<?xml version=\"1.0\"?>", "COMMENT:<!-- c -->", "TAG:a", "ATTRIBUTE:href",
                "STRING:\"x\"", "ATTRIBUTE:b", "STRING:'y'", "TAG:a", "TAG:br"), show(xml, SyntaxLanguage.XML));
    }

    @Test
    public void markdownHeadingsFencesAndInlineCode() {
        final String md = "# Titel\n\nText mit `code` und mehr.\n\n```java\nint x;\n```\n> Zitat\n#kein Titel";

        assertEquals(List.of("HEADING:# Titel", "STRING:`code`", "STRING:```java", "STRING:int x;", "STRING:```",
                "COMMENT:> Zitat"), show(md, SyntaxLanguage.MARKDOWN));
    }

    @Test
    public void cssAtRulesPropertiesAndColors() {
        final String css = "@media print { a { color: #fff; margin: 10px; } } /* c */";

        assertEquals(List.of("KEYWORD:@media", "ATTRIBUTE:color", "NUMBER:#fff", "ATTRIBUTE:margin", "NUMBER:10px",
                "COMMENT:/* c */"), show(css, SyntaxLanguage.CSS));
    }

    @Test
    public void propertiesCommentsSectionsAndKeys() {
        final String ini = "# Kommentar\n[abschnitt]\nschluessel = wert\nzahl=5\n; noch einer";

        assertEquals(List.of("COMMENT:# Kommentar", "HEADING:[abschnitt]", "KEY:schluessel", "KEY:zahl", "NUMBER:5",
                "COMMENT:; noch einer"), show(ini, SyntaxLanguage.PROPERTIES));
    }

    @Test
    public void gitignoreOnlyColorsComments() {
        assertEquals(List.of("COMMENT:# Build", "COMMENT:# eingerückt"),
                show("# Build\n*.apk\n  # eingerückt\nbuild/", SyntaxLanguage.HASH_COMMENTS));
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Rand- und Sonderfälle                                                                      */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    public void plainEmptyAndOversizedTextGetNoTokens() {
        assertTrue(SyntaxHighlighter.highlight("int x;", SyntaxLanguage.PLAIN).isEmpty());
        assertTrue(SyntaxHighlighter.highlight("", SyntaxLanguage.JAVA_LIKE).isEmpty());
        assertTrue(SyntaxHighlighter.highlight(null, SyntaxLanguage.JAVA_LIKE).isEmpty());
        assertTrue(SyntaxHighlighter.highlight("a".repeat(SyntaxHighlighter.MAX_CHARS + 1), SyntaxLanguage.JAVA_LIKE).isEmpty());
    }

    @Test
    public void windowsLineEndingsDoNotBreakLineBasedFormats() {
        final String text = "# Kommentar\r\nname: wert\r\n";

        assertEquals(List.of("COMMENT:# Kommentar\r", "KEY:name"), show(text, SyntaxLanguage.YAML));
    }

    @Test
    public void tokensAreSortedInBoundsAndNeverOverlapForAnyText() {
        final String alphabet = "abcXYZ019 _$\t\n\r\"'`/*#<>!?-=:;.,{}[]()\\@&%";
        final Random random = new Random(42);
        for (final SyntaxLanguage language : SyntaxLanguage.values()) {
            for (int round = 0; round < 300; round += 1) {
                final StringBuilder text = new StringBuilder();
                final int length = random.nextInt(120);
                for (int index = 0; index < length; index += 1) {
                    text.append(alphabet.charAt(random.nextInt(alphabet.length())));
                }
                assertInvariants(text.toString(), language);
            }
        }
    }

    @Test
    public void realisticSnippetsKeepTheInvariantsToo() {
        final String[] snippets = {
                "<a b=\"c", "<!-- offen", "<![CDATA[", "\"\"\"offen", "/* offen", "`offen", "${", "$", "#", "--",
                "'a", "\"a", "[abschnitt", "key:", "- - - x: y", "```", "> > >"
        };
        for (final SyntaxLanguage language : SyntaxLanguage.values()) {
            for (final String snippet : snippets) {
                assertInvariants(snippet, language);
                assertInvariants(snippet + "\n" + snippet, language);
            }
        }
    }

    private static void assertInvariants(
            final String text,
            final SyntaxLanguage language
    ) {
        int previousEnd = 0;
        for (final SyntaxToken token : SyntaxHighlighter.highlight(text, language)) {
            assertTrue(language + " " + text, token.start() >= previousEnd);
            assertTrue(language + " " + text, token.end() > token.start());
            assertTrue(language + " " + text, token.end() <= text.length());
            previousEnd = token.end();
        }
    }

    @Test
    public void typesAreAllUsedByAtLeastOneLanguage() {
        final List<Type> seen = new ArrayList<>();
        final String[][] samples = {
                {"public 1 \"s\" true // c", "JAVA_LIKE"}, {"<a b=\"c\">", "XML"}, {"{\"k\": 1}", "JSON"}, {"# t", "MARKDOWN"}
        };
        for (final String[] sample : samples) {
            for (final SyntaxToken token : SyntaxHighlighter.highlight(sample[0], SyntaxLanguage.valueOf(sample[1]))) {
                seen.add(token.type());
            }
        }
        for (final Type type : Type.values()) {
            assertTrue(type.name(), seen.contains(type));
        }
    }
}
