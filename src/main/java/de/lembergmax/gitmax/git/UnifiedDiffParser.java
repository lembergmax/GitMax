package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.DiffHunk;
import de.lembergmax.gitmax.domain.model.DiffLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Zerlegt die Ausgabe eines Unified-Diffs für eine Datei in Abschnitte und Zeilen mit Zeilennummern. */
final class UnifiedDiffParser {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    private UnifiedDiffParser() {
    }

    /**
     * @param diff Text eines Diffs; Kopfzeilen vor dem ersten {@code @@} werden übersprungen
     * @return die Abschnitte in Reihenfolge, leer wenn es keine gibt
     */
    @NonNull
    static List<DiffHunk> parse(
            @NonNull final String diff
    ) {
        Objects.requireNonNull(diff, "diff");
        final List<DiffHunk> hunks = new ArrayList<>();
        String header = null;
        List<DiffLine> lines = new ArrayList<>();
        int oldNumber = 0;
        int newNumber = 0;
        for (final String line : diff.split("\n", -1)) {
            final Matcher matcher = HUNK_HEADER.matcher(line);
            if (matcher.matches()) {
                if (header != null) {
                    hunks.add(new DiffHunk(header, lines));
                }
                header = line;
                lines = new ArrayList<>();
                oldNumber = Integer.parseInt(matcher.group(1));
                newNumber = Integer.parseInt(matcher.group(3));
                continue;
            }
            if (header == null) {
                continue;
            }
            if (line.startsWith("+")) {
                lines.add(new DiffLine(DiffLine.Type.ADDED, 0, newNumber, line.substring(1)));
                newNumber += 1;
            } else if (line.startsWith("-")) {
                lines.add(new DiffLine(DiffLine.Type.REMOVED, oldNumber, 0, line.substring(1)));
                oldNumber += 1;
            } else if (line.startsWith(" ")) {
                lines.add(new DiffLine(DiffLine.Type.CONTEXT, oldNumber, newNumber, line.substring(1)));
                oldNumber += 1;
                newNumber += 1;
            }
            // "\ No newline at end of file" und die leere Zeile nach dem letzten Zeilenende bleiben außen vor.
        }
        if (header != null) {
            hunks.add(new DiffHunk(header, lines));
        }
        return hunks;
    }
}
