package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Ein Commit für Listen und Details.
 *
 * @param id          vollständige Kennung
 * @param subject     erste Zeile der Nachricht
 * @param message     ganze Nachricht
 * @param authorName  Name des Autors
 * @param authorEmail E-Mail des Autors
 * @param timeMillis  Zeitpunkt des Commits in Millisekunden seit 1970
 * @param parents     Kennungen der Eltern-Commits
 * @param refs        Marken, die auf diesen Commit zeigen
 */
public record CommitInfo(
        @NonNull String id,
        @NonNull String subject,
        @NonNull String message,
        @NonNull String authorName,
        @NonNull String authorEmail,
        long timeMillis,
        @NonNull List<String> parents,
        @NonNull List<RefLabel> refs
) {

    /** Länge der gekürzten Kennung. */
    public static final int SHORT_ID_LENGTH = 7;

    public CommitInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(authorName, "authorName");
        Objects.requireNonNull(authorEmail, "authorEmail");
        parents = List.copyOf(Objects.requireNonNull(parents, "parents"));
        refs = List.copyOf(Objects.requireNonNull(refs, "refs"));
    }

    /** Die gekürzte Kennung. */
    @NonNull
    public String shortId() {
        return id.substring(0, Math.min(SHORT_ID_LENGTH, id.length()));
    }

    /** {@code true} für Merge-Commits. */
    public boolean isMerge() {
        return parents.size() > 1;
    }
}
