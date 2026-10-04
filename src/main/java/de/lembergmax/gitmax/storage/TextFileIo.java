package de.lembergmax.gitmax.storage;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;

/**
 * Liest und schreibt Textdateien so, dass ein Speichern nichts verändert, was der Nutzer nicht angefasst
 * hat: Zeilenenden (LF oder CRLF) und eine UTF-8-Markierung (BOM) bleiben erhalten. Geschrieben wird
 * atomar über eine Temp-Datei. Gelesen wird nur gültiges UTF-8 ohne Nullbytes; alles andere meldet das
 * Ergebnis als solches, statt Zeichen zu verfälschen.
 */
public final class TextFileIo {

    /** Größte Datei, die der Viewer noch öffnet, ohne nachzufragen. */
    public static final long VIEW_LIMIT_BYTES = 2L * 1024 * 1024;

    /** Größte Datei, die der Editor bearbeiten lässt. */
    public static final long EDIT_LIMIT_BYTES = 1024L * 1024;

    private static final int BINARY_PROBE_BYTES = 8000;
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private TextFileIo() {
    }

    /** Zeilenenden einer Datei. */
    public enum LineEnding {
        /** Nur {@code \n}. */
        LF,
        /** Nur {@code \r\n}. */
        CRLF,
        /** Beides gemischt: wird nicht bearbeitet, weil Speichern sie vereinheitlichen würde. */
        MIXED
    }

    /** Was beim Lesen herauskam. */
    public enum Kind {
        TEXT,
        BINARY,
        NOT_UTF8,
        TOO_LARGE
    }

    /**
     * Ergebnis des Lesens.
     *
     * @param kind         Art des Inhalts
     * @param text         Text mit {@code \n} als Zeilenende; leer, wenn {@code kind} nicht {@link Kind#TEXT} ist
     * @param lineEnding   ursprüngliche Zeilenenden
     * @param bom          Datei begann mit einer UTF-8-Markierung
     * @param size         Größe der Datei in Byte
     * @param lastModified Zeitpunkt der letzten Änderung beim Lesen, für die Prüfung vor dem Speichern
     */
    public record Result(
            @NonNull Kind kind,
            @NonNull String text,
            @NonNull LineEnding lineEnding,
            boolean bom,
            long size,
            long lastModified
    ) {

        public Result {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(lineEnding, "lineEnding");
        }

        /** {@code true}, wenn der Editor den Text bearbeiten darf. */
        public boolean isEditable() {
            return kind == Kind.TEXT && lineEnding != LineEnding.MIXED && size <= EDIT_LIMIT_BYTES;
        }
    }

    /**
     * Liest eine Datei.
     *
     * @param limitBytes größere Dateien werden nicht gelesen ({@link Kind#TOO_LARGE})
     */
    @NonNull
    public static Result read(
            @NonNull final File file,
            final long limitBytes
    ) throws IOException {
        Objects.requireNonNull(file, "file");
        final long size = file.length();
        final long modified = file.lastModified();
        if (size > limitBytes) {
            return new Result(Kind.TOO_LARGE, "", LineEnding.LF, false, size, modified);
        }
        final byte[] bytes = Files.readAllBytes(file.toPath());
        final boolean bom = startsWithBom(bytes);
        final int offset = bom ? BOM.length : 0;
        if (looksBinary(bytes, offset)) {
            return new Result(Kind.BINARY, "", LineEnding.LF, bom, size, modified);
        }
        final String decoded;
        try {
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (final CharacterCodingException notUtf8) {
            return new Result(Kind.NOT_UTF8, "", LineEnding.LF, bom, size, modified);
        }
        final LineEnding ending = detectLineEnding(decoded);
        final String text = decoded.contains("\r\n") ? decoded.replace("\r\n", "\n") : decoded;
        return new Result(Kind.TEXT, text, ending, bom, size, modified);
    }

    /**
     * Schreibt den Text atomar zurück: erst in eine Temp-Datei im selben Ordner, dann umbenennen. Das
     * Zeilenende {@code \n} des Editors wird in das ursprüngliche der Datei übersetzt.
     */
    public static void write(
            @NonNull final File file,
            @NonNull final String text,
            @NonNull final LineEnding lineEnding,
            final boolean bom
    ) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(text, "text");
        final String encoded = lineEnding == LineEnding.CRLF ? text.replace("\n", "\r\n") : text;
        final byte[] body = encoded.getBytes(StandardCharsets.UTF_8);
        final byte[] bytes = bom ? concat(BOM, body) : body;
        final File temp = new File(file.getParentFile(), "." + file.getName() + ".gitmax-tmp");
        try {
            Files.write(temp.toPath(), bytes);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    /** {@code true}, wenn die Datei seit dem Lesen verändert wurde (Größe oder Zeitstempel). */
    public static boolean changedSince(
            @NonNull final File file,
            @NonNull final Result readEarlier
    ) {
        return file.length() != readEarlier.size() || file.lastModified() != readEarlier.lastModified();
    }

    private static boolean startsWithBom(
            final byte[] bytes
    ) {
        return bytes.length >= BOM.length && Arrays.equals(Arrays.copyOf(bytes, BOM.length), BOM);
    }

    /** Ein Nullbyte in den ersten Bytes macht eine Datei zur Binärdatei (wie bei Git selbst). */
    private static boolean looksBinary(
            final byte[] bytes,
            final int offset
    ) {
        final int end = Math.min(bytes.length, offset + BINARY_PROBE_BYTES);
        for (int index = offset; index < end; index += 1) {
            if (bytes[index] == 0) {
                return true;
            }
        }
        return false;
    }

    private static LineEnding detectLineEnding(
            final String text
    ) {
        int crlf = 0;
        int lf = 0;
        for (int index = text.indexOf('\n'); index >= 0; index = text.indexOf('\n', index + 1)) {
            if (index > 0 && text.charAt(index - 1) == '\r') {
                crlf += 1;
            } else {
                lf += 1;
            }
        }
        if (crlf > 0 && lf > 0) {
            return LineEnding.MIXED;
        }
        return crlf > 0 ? LineEnding.CRLF : LineEnding.LF;
    }

    private static byte[] concat(
            final byte[] first,
            final byte[] second
    ) {
        final byte[] joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }
}
