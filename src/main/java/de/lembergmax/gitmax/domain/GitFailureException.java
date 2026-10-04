package de.lembergmax.gitmax.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.model.GitFailureKind;

import java.util.List;
import java.util.Objects;

/**
 * Ein Git-Vorgang ist gescheitert. {@link #kind()} sagt, was zu tun ist; die Meldung ist technisch und
 * enthält nie einen Token. Bei Konflikten und ungültigen Pfaden nennt {@link #paths()} die betroffenen Dateien.
 */
public final class GitFailureException extends Exception {

    private final GitFailureKind kind;
    private final List<String> paths;

    public GitFailureException(
            @NonNull final GitFailureKind kind,
            @NonNull final String message,
            @Nullable final Throwable cause
    ) {
        this(kind, message, List.of(), cause);
    }

    public GitFailureException(
            @NonNull final GitFailureKind kind,
            @NonNull final String message,
            @NonNull final List<String> paths,
            @Nullable final Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.paths = List.copyOf(Objects.requireNonNull(paths, "paths"));
    }

    @NonNull
    public GitFailureKind kind() {
        return kind;
    }

    /** Betroffene Dateien (Konflikte, ungültige Namen), sonst leer. */
    @NonNull
    public List<String> paths() {
        return paths;
    }
}
