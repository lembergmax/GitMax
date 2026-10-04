package de.lembergmax.gitmax.domain.syntax;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.syntax.SyntaxToken.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leichte Syntaxfärbung: ein einzelner Durchlauf ohne Parser, der Kommentare, Zeichenketten, Zahlen, Schlüsselwörter
 * und je nach Sprache Tags, Schlüssel und Überschriften findet. Er ist bewusst ungenau (kein Typ- oder Gültigkeitswissen),
 * wirft nie und liefert immer sortierte, überlappungsfreie Abschnitte innerhalb des Textes.
 */
public final class SyntaxHighlighter {

    /** Längere Texte werden nicht gefärbt: Das Setzen vieler Spans auf dem Telefon würde den Editor ausbremsen. */
    public static final int MAX_CHARS = 200_000;

    private static final Pattern YAML_KEY = Pattern.compile(
            "^(\\s*(?:-\\s+)*)(\"[^\"]*\"|'[^']*'|[^\\s:#'\"\\-\\[\\]{},&*!|>%@`][^:#]*?)\\s*:(?=\\s|$)");
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{3,8}\\b");
    private static final Pattern NUMBER = Pattern.compile(
            "0[xX][0-9a-fA-F_]+[lLuU]*|0[bB][01_]+|(?:\\d[\\d_]*(?:\\.\\d+)?|\\.\\d+)(?:[eE][+-]?\\d+)?[a-zA-Z%]*");

    /** Längstes Zeichenliteral mit Escape (Rust: Unicode-Escape in geschweiften Klammern), vom öffnenden bis zum schließenden Anführungszeichen. */
    private static final int MAX_ESCAPED_CHAR_LITERAL = 12;

    private static final Set<String> LITERALS = Set.of("true", "false", "null", "nil", "undefined", "NaN", "None", "True", "False");

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "as", "assert", "break", "case", "catch", "class", "companion", "const", "continue", "data", "def",
            "default", "do", "else", "enum", "extends", "final", "finally", "for", "fun", "if", "implements", "import", "in",
            "inline", "instanceof", "interface", "internal", "is", "lateinit", "native", "new", "object", "open", "override",
            "package", "permits", "private", "protected", "public", "record", "return", "sealed", "static", "super", "switch",
            "synchronized", "this", "throw", "throws", "trait", "transient", "try", "typealias", "val", "var", "void",
            "volatile", "when", "while", "yield", "boolean", "byte", "char", "double", "float", "int", "long", "short");

    private static final Set<String> SCRIPT_KEYWORDS = Set.of(
            "abstract", "as", "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "declare",
            "default", "delete", "do", "else", "enum", "export", "extends", "finally", "for", "from", "function", "if",
            "implements", "import", "in", "instanceof", "interface", "let", "namespace", "new", "of", "private", "protected",
            "public", "readonly", "return", "static", "super", "switch", "this", "throw", "try", "type", "typeof", "var",
            "void", "while", "with", "yield");

    private static final Set<String> C_KEYWORDS = Set.of(
            "auto", "bool", "break", "case", "catch", "char", "chan", "class", "const", "continue", "crate", "default",
            "defer", "define", "delete", "do", "double", "dyn", "else", "enum", "extern", "float", "fn", "for", "func",
            "go", "goto", "if", "impl", "import", "include", "inline", "int", "interface", "let", "long", "loop", "map",
            "match", "mod", "move", "mut", "namespace", "new", "operator", "package", "pragma", "private", "protected",
            "pub", "public", "range", "ref", "register", "return", "select", "self", "short", "signed", "sizeof", "static",
            "struct", "switch", "template", "this", "throw", "trait", "try", "type", "typedef", "typename", "union",
            "unsafe", "unsigned", "use", "using", "var", "virtual", "void", "volatile", "where", "while", "function",
            "echo", "foreach", "elseif", "async", "await");

    private static final Set<String> PYTHON_KEYWORDS = Set.of(
            "and", "as", "assert", "async", "await", "break", "case", "class", "continue", "def", "del", "elif", "else",
            "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "match", "nonlocal", "not",
            "or", "pass", "raise", "return", "try", "while", "with", "yield");

    private static final Set<String> SHELL_KEYWORDS = Set.of(
            "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case", "esac", "in", "function",
            "select", "return", "exit", "break", "continue", "local", "export", "readonly", "declare", "unset", "shift",
            "source", "alias", "echo", "cd");

    private static final Set<String> SQL_KEYWORDS = Set.of(
            "select", "from", "where", "insert", "into", "values", "update", "set", "delete", "create", "table", "alter",
            "drop", "index", "view", "join", "left", "right", "inner", "outer", "full", "cross", "on", "group", "by", "order",
            "having", "limit", "offset", "union", "all", "distinct", "as", "and", "or", "not", "is", "in", "like", "between",
            "exists", "case", "when", "then", "else", "end", "primary", "key", "foreign", "references", "default", "unique",
            "check", "constraint", "begin", "commit", "rollback", "with", "asc", "desc");

    private SyntaxHighlighter() {
    }

    /** Wie ein Code-Durchlauf Text zerlegt; wird mit {@link #rules()} und den Methoden darauf zusammengesetzt. */
    private static final class Rules {

        private String lineComment = "";
        private boolean blockComments;
        private String quotes = "\"'";
        private String multilineQuotes = "";
        private boolean tripleQuotes;
        private Set<String> keywords = Set.of();
        private boolean ignoreCase;
        private boolean shellVariables;
        private boolean boundaryComments;
        private boolean css;
        private boolean jsonKeys;
        private boolean charLiterals;

        Rules lineComment(
                final String prefix
        ) {
            this.lineComment = prefix;
            return this;
        }

        Rules blockComments() {
            this.blockComments = true;
            return this;
        }

        Rules quotes(
                final String chars
        ) {
            this.quotes = chars;
            return this;
        }

        Rules multilineQuotes(
                final String chars
        ) {
            this.multilineQuotes = chars;
            return this;
        }

        Rules tripleQuotes() {
            this.tripleQuotes = true;
            return this;
        }

        Rules keywords(
                final Set<String> words
        ) {
            this.keywords = words;
            return this;
        }

        Rules ignoreCase() {
            this.ignoreCase = true;
            return this;
        }

        Rules shellVariables() {
            this.shellVariables = true;
            return this;
        }

        /** Ein Kommentar beginnt nur am Zeilenanfang oder nach Leerraum (Shell, YAML): {@code a#b} ist kein Kommentar. */
        Rules boundaryComments() {
            this.boundaryComments = true;
            return this;
        }

        Rules css() {
            this.css = true;
            return this;
        }

        Rules jsonKeys() {
            this.jsonKeys = true;
            return this;
        }

        /** {@code '} öffnet nur dann eine Zeichenkette, wenn bald ein schließendes folgt (Rust-Lebenszeiten wie {@code 'a}). */
        Rules charLiterals() {
            this.charLiterals = true;
            return this;
        }
    }

    private static Rules rules() {
        return new Rules();
    }

    /**
     * @return die gefärbten Abschnitte, sortiert und ohne Überlappung; leer für unbekannte Sprachen, leeren Text und
     *         Text über {@link #MAX_CHARS}
     */
    @NonNull
    public static List<SyntaxToken> highlight(
            final CharSequence text,
            @NonNull final SyntaxLanguage language
    ) {
        if (text == null || text.length() == 0 || text.length() > MAX_CHARS || language == SyntaxLanguage.PLAIN) {
            return List.of();
        }
        final String source = text.toString();
        final int length = source.length();
        final List<SyntaxToken> tokens = new ArrayList<>();
        switch (language) {
            case JAVA_LIKE:
                scan(source, 0, length, rules().lineComment("//").blockComments().tripleQuotes().keywords(JAVA_KEYWORDS), tokens);
                break;
            case SCRIPT:
                scan(source, 0, length, rules().lineComment("//").blockComments().multilineQuotes("`").keywords(SCRIPT_KEYWORDS), tokens);
                break;
            case C_LIKE:
                scan(source, 0, length, rules().lineComment("//").blockComments().charLiterals().keywords(C_KEYWORDS), tokens);
                break;
            case PYTHON:
                scan(source, 0, length, rules().lineComment("#").tripleQuotes().keywords(PYTHON_KEYWORDS), tokens);
                break;
            case SHELL:
                scan(source, 0, length, rules().lineComment("#").boundaryComments().shellVariables().keywords(SHELL_KEYWORDS), tokens);
                break;
            case SQL:
                scan(source, 0, length, rules().lineComment("--").blockComments().ignoreCase().keywords(SQL_KEYWORDS), tokens);
                break;
            case JSON:
                scan(source, 0, length, rules().lineComment("//").blockComments().quotes("\"").jsonKeys(), tokens);
                break;
            case CSS:
                scan(source, 0, length, rules().blockComments().css(), tokens);
                break;
            case YAML:
                yaml(source, tokens);
                break;
            case XML:
                xml(source, tokens);
                break;
            case MARKDOWN:
                markdown(source, tokens);
                break;
            case PROPERTIES:
                properties(source, tokens);
                break;
            case HASH_COMMENTS:
                hashComments(source, tokens);
                break;
            case PLAIN:
            default:
                break;
        }
        return tokens;
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Code                                                                                       */
    /* ------------------------------------------------------------------------------------------ */

    private static void scan(
            final String text,
            final int from,
            final int to,
            final Rules rules,
            final List<SyntaxToken> out
    ) {
        int braceDepth = 0;
        int index = from;
        while (index < to) {
            final char c = text.charAt(index);
            if (Character.isWhitespace(c)) {
                index += 1;
                continue;
            }
            if (rules.blockComments && text.startsWith("/*", index)) {
                final int close = text.indexOf("*/", index + 2);
                final int end = close < 0 || close + 2 > to ? to : close + 2;
                out.add(new SyntaxToken(index, end, Type.COMMENT));
                index = end;
                continue;
            }
            if (!rules.lineComment.isEmpty() && text.startsWith(rules.lineComment, index)
                    && commentMayStartAt(text, index, from, rules)) {
                final int end = lineEnd(text, index, to);
                out.add(new SyntaxToken(index, end, Type.COMMENT));
                index = end;
                continue;
            }
            if (rules.tripleQuotes && (text.startsWith("\"\"\"", index) || text.startsWith("'''", index))) {
                final String triple = text.substring(index, index + 3);
                final int close = text.indexOf(triple, index + 3);
                final int end = close < 0 || close + 3 > to ? to : close + 3;
                out.add(new SyntaxToken(index, end, Type.STRING));
                index = end;
                continue;
            }
            if (opensString(text, index, to, rules)) {
                final int end = stringEnd(text, index, to, rules.multilineQuotes.indexOf(c) >= 0);
                out.add(stringToken(text, index, end, to, rules));
                index = end;
                continue;
            }
            if (rules.shellVariables && c == '$') {
                final int end = shellVariableEnd(text, index, to);
                if (end > index + 1) {
                    out.add(new SyntaxToken(index, end, Type.ATTRIBUTE));
                    index = end;
                    continue;
                }
            }
            if (rules.css) {
                final int consumed = cssToken(text, index, to, braceDepth, out);
                if (consumed > index) {
                    index = consumed;
                    continue;
                }
                if (c == '{') {
                    braceDepth += 1;
                } else if (c == '}' && braceDepth > 0) {
                    braceDepth -= 1;
                }
            }
            if (Character.isDigit(c) || (c == '.' && index + 1 < to && Character.isDigit(text.charAt(index + 1)))) {
                final Matcher matcher = NUMBER.matcher(text);
                matcher.region(index, to);
                if (matcher.lookingAt()) {
                    out.add(new SyntaxToken(index, matcher.end(), Type.NUMBER));
                    index = matcher.end();
                    continue;
                }
            }
            if (Character.isLetter(c) || c == '_' || c == '$') {
                int end = index + 1;
                while (end < to && isIdentifierPart(text.charAt(end))) {
                    end += 1;
                }
                final String word = text.substring(index, end);
                final String key = rules.ignoreCase ? word.toLowerCase(Locale.ROOT) : word;
                if (rules.keywords.contains(key)) {
                    out.add(new SyntaxToken(index, end, Type.KEYWORD));
                } else if (LITERALS.contains(word)) {
                    out.add(new SyntaxToken(index, end, Type.LITERAL));
                }
                index = end;
                continue;
            }
            index += 1;
        }
    }

    /** Bei Regeln mit {@code boundaryComments} beginnt ein Kommentar nur an einer Wortgrenze. */
    private static boolean commentMayStartAt(
            final String text,
            final int index,
            final int from,
            final Rules rules
    ) {
        return !rules.boundaryComments || index == from || Character.isWhitespace(text.charAt(index - 1));
    }

    private static boolean opensString(
            final String text,
            final int index,
            final int to,
            final Rules rules
    ) {
        final char c = text.charAt(index);
        if (rules.multilineQuotes.indexOf(c) >= 0) {
            return true;
        }
        if (rules.quotes.indexOf(c) < 0) {
            return false;
        }
        if (c == '\'' && rules.charLiterals) {
            return isCharLiteral(text, index, to);
        }
        return true;
    }

    /**
     * Ein Zeichenliteral hat genau ein Zeichen ({@code 'x'}, auch ein Ersatzpaar) oder einen Escape zwischen den
     * Anführungszeichen; {@code 'a} in {@code <'a>} ist keines.
     */
    private static boolean isCharLiteral(
            final String text,
            final int open,
            final int to
    ) {
        if (open + 2 >= to) {
            return false;
        }
        final char first = text.charAt(open + 1);
        if (first == '\\') {
            final int close = text.indexOf('\'', open + 3);
            final int newline = text.indexOf('\n', open);
            return close > open && close <= open + MAX_ESCAPED_CHAR_LITERAL && close < to && (newline < 0 || newline > close);
        }
        if (first == '\n' || first == '\'') {
            return false;
        }
        final int single = open + (Character.isHighSurrogate(first) ? 3 : 2);
        return single < to && text.charAt(single) == '\'';
    }

    /** Ein String ist in JSON ein Schlüssel, wenn danach (nach Leerraum) ein Doppelpunkt kommt. */
    private static SyntaxToken stringToken(
            final String text,
            final int start,
            final int end,
            final int to,
            final Rules rules
    ) {
        if (rules.jsonKeys) {
            int look = end;
            while (look < to && (text.charAt(look) == ' ' || text.charAt(look) == '\t')) {
                look += 1;
            }
            if (look < to && text.charAt(look) == ':') {
                return new SyntaxToken(start, end, Type.KEY);
            }
        }
        return new SyntaxToken(start, end, Type.STRING);
    }

    /**
     * Ende einer Zeichenkette: nach dem schließenden Anführungszeichen; einfache Strings enden spätestens am
     * Zeilenende (ein vergessenes Anführungszeichen färbt nicht den Rest der Datei), Backslash schützt das nächste Zeichen.
     */
    private static int stringEnd(
            final String text,
            final int start,
            final int to,
            final boolean multiline
    ) {
        final char quote = text.charAt(start);
        int index = start + 1;
        while (index < to) {
            final char c = text.charAt(index);
            if (c == '\\' && index + 1 < to) {
                index += 2;
                continue;
            }
            if (c == quote) {
                return index + 1;
            }
            if (c == '\n' && !multiline) {
                return index;
            }
            index += 1;
        }
        return to;
    }

    private static int shellVariableEnd(
            final String text,
            final int start,
            final int to
    ) {
        int index = start + 1;
        if (index >= to) {
            return start;
        }
        final char c = text.charAt(index);
        if (c == '{') {
            final int close = text.indexOf('}', index);
            final int newline = text.indexOf('\n', index);
            return close < 0 || close >= to || (newline >= 0 && newline < close) ? start : close + 1;
        }
        if (c == '#' || c == '?' || c == '@' || c == '*' || c == '$' || c == '!' || Character.isDigit(c)) {
            return index + 1;
        }
        while (index < to && (Character.isLetterOrDigit(text.charAt(index)) || text.charAt(index) == '_')) {
            index += 1;
        }
        return index;
    }

    /** CSS: At-Regeln, Farben und Eigenschaftsnamen innerhalb von Blöcken. Liefert das neue Ende oder {@code start}. */
    private static int cssToken(
            final String text,
            final int start,
            final int to,
            final int braceDepth,
            final List<SyntaxToken> out
    ) {
        final char c = text.charAt(start);
        if (c == '@') {
            int end = start + 1;
            while (end < to && (Character.isLetter(text.charAt(end)) || text.charAt(end) == '-')) {
                end += 1;
            }
            if (end > start + 1) {
                out.add(new SyntaxToken(start, end, Type.KEYWORD));
                return end;
            }
        }
        if (c == '#') {
            final Matcher matcher = HEX_COLOR.matcher(text);
            matcher.region(start, to);
            if (matcher.lookingAt()) {
                out.add(new SyntaxToken(start, matcher.end(), Type.NUMBER));
                return matcher.end();
            }
        }
        if (braceDepth > 0 && (Character.isLetter(c) || c == '-')) {
            int end = start + 1;
            while (end < to && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '-')) {
                end += 1;
            }
            int look = end;
            while (look < to && (text.charAt(look) == ' ' || text.charAt(look) == '\t')) {
                look += 1;
            }
            if (look < to && text.charAt(look) == ':') {
                out.add(new SyntaxToken(start, end, Type.ATTRIBUTE));
            }
            return end;
        }
        return start;
    }

    private static boolean isIdentifierPart(
            final char c
    ) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static int lineEnd(
            final String text,
            final int from,
            final int to
    ) {
        final int newline = text.indexOf('\n', from);
        return newline < 0 || newline > to ? to : newline;
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Zeilenorientierte Formate                                                                  */
    /* ------------------------------------------------------------------------------------------ */

    private static void yaml(
            final String text,
            final List<SyntaxToken> out
    ) {
        final Rules value = rules().lineComment("#").boundaryComments();
        int lineStart = 0;
        while (lineStart < text.length()) {
            final int lineEnd = lineEnd(text, lineStart, text.length());
            final String line = text.substring(lineStart, lineEnd);
            int valueFrom = lineStart;
            final Matcher key = YAML_KEY.matcher(line);
            if (key.find()) {
                out.add(new SyntaxToken(lineStart + key.start(2), lineStart + key.end(2), Type.KEY));
                valueFrom = lineStart + key.end();
            }
            scan(text, valueFrom, lineEnd, value, out);
            lineStart = lineEnd + 1;
        }
    }

    private static void properties(
            final String text,
            final List<SyntaxToken> out
    ) {
        final Rules value = rules();
        int lineStart = 0;
        while (lineStart < text.length()) {
            final int lineEnd = lineEnd(text, lineStart, text.length());
            int first = lineStart;
            while (first < lineEnd && (text.charAt(first) == ' ' || text.charAt(first) == '\t')) {
                first += 1;
            }
            if (first < lineEnd) {
                final char c = text.charAt(first);
                if (c == '#' || c == ';') {
                    out.add(new SyntaxToken(first, lineEnd, Type.COMMENT));
                } else if (c == '[') {
                    final int close = text.indexOf(']', first);
                    out.add(new SyntaxToken(first, close < 0 || close >= lineEnd ? lineEnd : close + 1, Type.HEADING));
                } else {
                    int separator = first;
                    while (separator < lineEnd && text.charAt(separator) != '=' && text.charAt(separator) != ':') {
                        separator += 1;
                    }
                    if (separator < lineEnd) {
                        int keyEnd = separator;
                        while (keyEnd > first && Character.isWhitespace(text.charAt(keyEnd - 1))) {
                            keyEnd -= 1;
                        }
                        if (keyEnd > first) {
                            out.add(new SyntaxToken(first, keyEnd, Type.KEY));
                        }
                        scan(text, separator + 1, lineEnd, value, out);
                    }
                }
            }
            lineStart = lineEnd + 1;
        }
    }

    private static void hashComments(
            final String text,
            final List<SyntaxToken> out
    ) {
        int lineStart = 0;
        while (lineStart < text.length()) {
            final int lineEnd = lineEnd(text, lineStart, text.length());
            int first = lineStart;
            while (first < lineEnd && (text.charAt(first) == ' ' || text.charAt(first) == '\t')) {
                first += 1;
            }
            if (first < lineEnd && text.charAt(first) == '#') {
                out.add(new SyntaxToken(first, lineEnd, Type.COMMENT));
            }
            lineStart = lineEnd + 1;
        }
    }

    private static void markdown(
            final String text,
            final List<SyntaxToken> out
    ) {
        boolean fenced = false;
        int lineStart = 0;
        while (lineStart < text.length()) {
            final int lineEnd = lineEnd(text, lineStart, text.length());
            final String trimmed = text.substring(lineStart, lineEnd).stripLeading();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                out.add(new SyntaxToken(lineStart, lineEnd, Type.STRING));
                fenced = !fenced;
            } else if (fenced) {
                if (lineEnd > lineStart) {
                    out.add(new SyntaxToken(lineStart, lineEnd, Type.STRING));
                }
            } else if (isHeading(trimmed)) {
                out.add(new SyntaxToken(lineStart, lineEnd, Type.HEADING));
            } else if (trimmed.startsWith(">")) {
                out.add(new SyntaxToken(lineStart, lineEnd, Type.COMMENT));
            } else {
                inlineCode(text, lineStart, lineEnd, out);
            }
            lineStart = lineEnd + 1;
        }
    }

    private static boolean isHeading(
            final String trimmed
    ) {
        int hashes = 0;
        while (hashes < trimmed.length() && trimmed.charAt(hashes) == '#') {
            hashes += 1;
        }
        return hashes >= 1 && hashes <= 6 && hashes < trimmed.length() && trimmed.charAt(hashes) == ' ';
    }

    private static void inlineCode(
            final String text,
            final int from,
            final int to,
            final List<SyntaxToken> out
    ) {
        int index = from;
        while (index < to) {
            if (text.charAt(index) == '`') {
                final int close = text.indexOf('`', index + 1);
                if (close > index && close < to) {
                    out.add(new SyntaxToken(index, close + 1, Type.STRING));
                    index = close + 1;
                    continue;
                }
            }
            index += 1;
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* XML und HTML                                                                               */
    /* ------------------------------------------------------------------------------------------ */

    private static void xml(
            final String text,
            final List<SyntaxToken> out
    ) {
        int index = 0;
        final int length = text.length();
        while (index < length) {
            final int open = text.indexOf('<', index);
            if (open < 0) {
                return;
            }
            if (text.startsWith("<!--", open)) {
                final int close = text.indexOf("-->", open + 4);
                final int end = close < 0 ? length : close + 3;
                out.add(new SyntaxToken(open, end, Type.COMMENT));
                index = end;
            } else if (text.startsWith("<![CDATA[", open)) {
                final int close = text.indexOf("]]>", open + 9);
                final int end = close < 0 ? length : close + 3;
                out.add(new SyntaxToken(open, end, Type.STRING));
                index = end;
            } else if (text.startsWith("<?", open) || text.startsWith("<!", open)) {
                final int close = text.indexOf('>', open + 2);
                final int end = close < 0 ? length : close + 1;
                out.add(new SyntaxToken(open, end, Type.KEYWORD));
                index = end;
            } else {
                index = xmlTag(text, open, out);
            }
        }
    }

    /** Färbt Tag-Name, Attribute und Werte eines Tags; liefert die Stelle nach seinem {@code >}. */
    private static int xmlTag(
            final String text,
            final int open,
            final List<SyntaxToken> out
    ) {
        final int length = text.length();
        int index = open + 1;
        if (index < length && text.charAt(index) == '/') {
            index += 1;
        }
        final int nameStart = index;
        while (index < length && isNameChar(text.charAt(index))) {
            index += 1;
        }
        if (index == nameStart) {
            return open + 1;
        }
        out.add(new SyntaxToken(nameStart, index, Type.TAG));
        while (index < length) {
            final char c = text.charAt(index);
            if (c == '>') {
                return index + 1;
            }
            if (c == '"' || c == '\'') {
                final int close = text.indexOf(c, index + 1);
                final int end = close < 0 ? length : close + 1;
                out.add(new SyntaxToken(index, end, Type.STRING));
                index = end;
            } else if (isNameChar(c)) {
                final int start = index;
                while (index < length && isNameChar(text.charAt(index))) {
                    index += 1;
                }
                out.add(new SyntaxToken(start, index, Type.ATTRIBUTE));
            } else {
                index += 1;
            }
        }
        return length;
    }

    private static boolean isNameChar(
            final char c
    ) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':' || c == '.';
    }
}
