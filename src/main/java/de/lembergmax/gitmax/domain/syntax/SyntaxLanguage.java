package de.lembergmax.gitmax.domain.syntax;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Map;

/** Die Sprachen, für die GitMax eine leichte Syntaxfärbung kennt, und ihre Zuordnung zu Dateinamen. */
public enum SyntaxLanguage {

    /** Java, Kotlin, Scala, Groovy, C#, Dart. */
    JAVA_LIKE,
    /** JavaScript und TypeScript. */
    SCRIPT,
    /** C, C++, Go, Rust, Swift, PHP. */
    C_LIKE,
    PYTHON,
    SHELL,
    JSON,
    YAML,
    XML,
    MARKDOWN,
    CSS,
    SQL,
    /** {@code .properties}, {@code .ini}, {@code .toml}, {@code .cfg}. */
    PROPERTIES,
    /** Nur Kommentare mit {@code #}: {@code .gitignore} und Verwandte. */
    HASH_COMMENTS,
    /** Keine Färbung. */
    PLAIN;

    private static final Map<String, SyntaxLanguage> BY_EXTENSION = Map.ofEntries(
            Map.entry("java", JAVA_LIKE), Map.entry("kt", JAVA_LIKE), Map.entry("kts", JAVA_LIKE),
            Map.entry("scala", JAVA_LIKE), Map.entry("groovy", JAVA_LIKE), Map.entry("gradle", JAVA_LIKE),
            Map.entry("cs", JAVA_LIKE), Map.entry("dart", JAVA_LIKE),
            Map.entry("js", SCRIPT), Map.entry("mjs", SCRIPT), Map.entry("cjs", SCRIPT), Map.entry("jsx", SCRIPT),
            Map.entry("ts", SCRIPT), Map.entry("tsx", SCRIPT),
            Map.entry("c", C_LIKE), Map.entry("h", C_LIKE), Map.entry("cpp", C_LIKE), Map.entry("cc", C_LIKE),
            Map.entry("hpp", C_LIKE), Map.entry("go", C_LIKE), Map.entry("rs", C_LIKE), Map.entry("swift", C_LIKE),
            Map.entry("php", C_LIKE),
            Map.entry("py", PYTHON), Map.entry("pyw", PYTHON),
            Map.entry("sh", SHELL), Map.entry("bash", SHELL), Map.entry("zsh", SHELL),
            Map.entry("json", JSON), Map.entry("jsonc", JSON),
            Map.entry("yml", YAML), Map.entry("yaml", YAML),
            Map.entry("xml", XML), Map.entry("html", XML), Map.entry("htm", XML), Map.entry("svg", XML),
            Map.entry("xsd", XML), Map.entry("xhtml", XML), Map.entry("plist", XML),
            Map.entry("md", MARKDOWN), Map.entry("markdown", MARKDOWN),
            Map.entry("css", CSS), Map.entry("scss", CSS),
            Map.entry("sql", SQL),
            Map.entry("properties", PROPERTIES), Map.entry("ini", PROPERTIES), Map.entry("toml", PROPERTIES),
            Map.entry("cfg", PROPERTIES), Map.entry("conf", PROPERTIES)
    );

    private static final Map<String, SyntaxLanguage> BY_NAME = Map.of(
            ".gitignore", HASH_COMMENTS, ".gitattributes", HASH_COMMENTS, ".dockerignore", HASH_COMMENTS,
            ".editorconfig", PROPERTIES, "dockerfile", SHELL, "makefile", HASH_COMMENTS, "gradlew", SHELL
    );

    /** Die Sprache zu einem Dateinamen; unbekannte Namen ergeben {@link #PLAIN}. */
    @NonNull
    public static SyntaxLanguage forFileName(
            @NonNull final String fileName
    ) {
        final String lower = fileName.toLowerCase(Locale.ROOT);
        final SyntaxLanguage byName = BY_NAME.get(lower);
        if (byName != null) {
            return byName;
        }
        final int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) {
            return PLAIN;
        }
        return BY_EXTENSION.getOrDefault(lower.substring(dot + 1), PLAIN);
    }
}
