package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Ein zusammenhängender Abschnitt eines Diffs.
 *
 * @param header Kopfzeile wie {@code @@ -3,7 +3,8 @@}
 * @param lines  Zeilen des Abschnitts
 */
public record DiffHunk(
        @NonNull String header,
        @NonNull List<DiffLine> lines
) {

    public DiffHunk {
        Objects.requireNonNull(header, "header");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }
}
