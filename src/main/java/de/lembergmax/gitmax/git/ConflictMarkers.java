package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import java.util.Objects;

/** Erkennt und entfernt Konfliktmarken ({@code <<<<<<<}, {@code =======}, {@code >>>>>>>}) in Text. */
public final class ConflictMarkers {

    private static final String OURS_START = "<<<<<<< ";
    private static final String BASE_START = "||||||| ";
    private static final String SEPARATOR = "=======";
    private static final String THEIRS_END = ">>>>>>> ";

    private enum Section {
        OUTSIDE,
        OURS,
        BASE,
        THEIRS
    }

    private ConflictMarkers() {
    }

    /** {@code true}, wenn der Text einen vollständigen Konfliktblock enthält. */
    public static boolean hasMarkers(
            @NonNull final String text
    ) {
        return blockCount(text) > 0;
    }

    /** Zahl der Konfliktblöcke. */
    public static int blockCount(
            @NonNull final String text
    ) {
        Objects.requireNonNull(text, "text");
        int count = 0;
        Section section = Section.OUTSIDE;
        for (final String line : text.split("\n", -1)) {
            final String clean = stripCarriageReturn(line);
            if (section == Section.OUTSIDE && clean.startsWith(OURS_START)) {
                section = Section.OURS;
            } else if (section == Section.OURS && clean.startsWith(BASE_START)) {
                section = Section.BASE;
            } else if ((section == Section.OURS || section == Section.BASE) && clean.equals(SEPARATOR)) {
                section = Section.THEIRS;
            } else if (section == Section.THEIRS && clean.startsWith(THEIRS_END)) {
                section = Section.OUTSIDE;
                count += 1;
            }
        }
        return count;
    }

    /**
     * Behält in jedem Konfliktblock beide Seiten, erst die eigene, dann die andere; Marken und der
     * gemeinsame Vorfahr (bei diff3-Darstellung) entfallen. Text ohne Block bleibt unverändert.
     */
    @NonNull
    public static String keepBoth(
            @NonNull final String text
    ) {
        Objects.requireNonNull(text, "text");
        final StringBuilder result = new StringBuilder(text.length());
        Section section = Section.OUTSIDE;
        final String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length; index += 1) {
            final String line = lines[index];
            final String clean = stripCarriageReturn(line);
            final boolean last = index == lines.length - 1;
            final String ending = last ? "" : "\n";
            if (section == Section.OUTSIDE && clean.startsWith(OURS_START)) {
                section = Section.OURS;
                continue;
            }
            if (section == Section.OURS && clean.startsWith(BASE_START)) {
                section = Section.BASE;
                continue;
            }
            if ((section == Section.OURS || section == Section.BASE) && clean.equals(SEPARATOR)) {
                section = Section.THEIRS;
                continue;
            }
            if (section == Section.THEIRS && clean.startsWith(THEIRS_END)) {
                section = Section.OUTSIDE;
                continue;
            }
            if (section == Section.BASE) {
                continue;
            }
            result.append(line).append(ending);
        }
        return result.toString();
    }

    private static String stripCarriageReturn(
            final String line
    ) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
