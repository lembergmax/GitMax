package de.lembergmax.gitmax.domain;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Erkennt Git LFS an dem, was im Arbeitsbaum liegt. GitMax lädt und sendet LFS-Inhalte nicht (siehe Spike-Befund in
 * {@code CLAUDE.md}): Eine LFS-Datei ist hier nur ihr Zeiger. Diese Klasse sorgt dafür, dass die Oberfläche das benennt.
 */
public final class GitLfs {

    /** Ein Zeiger auf ein Objekt auf dem LFS-Server. */
    public record Pointer(
            @NonNull String oid,
            long size
    ) {
    }

    /** Ein Zeiger ist ein kleiner Text; größere Dateien sind nie einer. */
    private static final int MAX_POINTER_CHARS = 1024;
    private static final String VERSION_LINE_PREFIX = "version https://git-lfs.github.com/spec/";
    private static final String LEGACY_VERSION_LINE_PREFIX = "version https://hawser.github.com/spec/";
    private static final String OID_PREFIX = "oid sha256:";
    private static final String SIZE_PREFIX = "size ";
    private static final int OID_LENGTH = 64;

    private GitLfs() {
    }

    /** Liest {@code text} als LFS-Zeiger; leer, wenn es keiner ist. */
    @NonNull
    public static Optional<Pointer> parsePointer(
            @NonNull final CharSequence text
    ) {
        Objects.requireNonNull(text, "text");
        if (text.length() == 0 || text.length() > MAX_POINTER_CHARS) {
            return Optional.empty();
        }
        final String[] lines = text.toString().split("\n", -1);
        if (!lines[0].startsWith(VERSION_LINE_PREFIX) && !lines[0].startsWith(LEGACY_VERSION_LINE_PREFIX)) {
            return Optional.empty();
        }
        String oid = null;
        long size = -1;
        for (int index = 1; index < lines.length; index += 1) {
            final String line = lines[index];
            if (line.startsWith(OID_PREFIX)) {
                oid = line.substring(OID_PREFIX.length());
            } else if (line.startsWith(SIZE_PREFIX)) {
                size = parseSize(line.substring(SIZE_PREFIX.length()));
            }
        }
        if (oid == null || !isHexOid(oid) || size < 0) {
            return Optional.empty();
        }
        return Optional.of(new Pointer(oid, size));
    }

    /** {@code true}, wenn die Datei {@code .gitattributes} irgendein Muster an den LFS-Filter bindet. */
    public static boolean usesLfs(
            @NonNull final CharSequence gitAttributes
    ) {
        Objects.requireNonNull(gitAttributes, "gitAttributes");
        for (final String line : gitAttributes.toString().split("\n")) {
            final String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            for (final String token : trimmed.split("\\s+")) {
                if (token.equals("filter=lfs")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long parseSize(
            final String digits
    ) {
        final String trimmed = digits.strip();
        if (trimmed.isEmpty() || trimmed.length() > 18) {
            return -1;
        }
        for (int index = 0; index < trimmed.length(); index += 1) {
            if (!Character.isDigit(trimmed.charAt(index))) {
                return -1;
            }
        }
        return Long.parseLong(trimmed);
    }

    private static boolean isHexOid(
            final String oid
    ) {
        if (oid.length() != OID_LENGTH) {
            return false;
        }
        for (int index = 0; index < oid.length(); index += 1) {
            final char character = oid.charAt(index);
            final boolean hex = (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}
