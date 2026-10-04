package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Ein Commit mit den Dateien, die er ändert.
 *
 * @param info  der Commit
 * @param files geänderte Dateien
 */
public record CommitDetail(
        @NonNull CommitInfo info,
        @NonNull List<FileChange> files
) {

    public CommitDetail {
        Objects.requireNonNull(info, "info");
        files = List.copyOf(Objects.requireNonNull(files, "files"));
    }
}
