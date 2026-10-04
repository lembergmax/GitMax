package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Objects;

/**
 * Auftrag zum Committen des Index.
 *
 * @param repo     Arbeitsverzeichnis
 * @param message  Commit-Nachricht, nicht leer
 * @param identity Autor und Committer
 * @param amend    den letzten Commit ersetzen statt einen neuen anzulegen
 */
public record CommitRequest(
        @NonNull File repo,
        @NonNull String message,
        @NonNull CommitIdentity identity,
        boolean amend
) {

    public CommitRequest {
        Objects.requireNonNull(repo, "repo");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(identity, "identity");
    }
}
