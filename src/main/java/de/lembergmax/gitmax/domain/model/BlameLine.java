package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Eine Zeile einer Datei samt dem Commit, der sie zuletzt geändert hat.
 *
 * @param number      Zeilennummer, ab 1
 * @param text        Inhalt der Zeile ohne Zeilenende
 * @param commitId    vollständige Kennung des letzten ändernden Commits, leer bei Zeilen, die noch in keinem Commit stehen
 * @param authorName  Name des Autors dieses Commits
 * @param timeMillis  Zeitpunkt des Commits in Millisekunden seit 1970
 * @param subject     erste Zeile der Commit-Nachricht
 */
public record BlameLine(
        int number,
        @NonNull String text,
        @NonNull String commitId,
        @NonNull String authorName,
        long timeMillis,
        @NonNull String subject
) {

    public BlameLine {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(commitId, "commitId");
        Objects.requireNonNull(authorName, "authorName");
        Objects.requireNonNull(subject, "subject");
    }

    /** {@code false} für Zeilen, die nur im Arbeitsverzeichnis stehen. */
    public boolean isCommitted() {
        return !commitId.isEmpty();
    }

    /** Die gekürzte Kennung des Commits. */
    @NonNull
    public String shortId() {
        return commitId.substring(0, Math.min(CommitInfo.SHORT_ID_LENGTH, commitId.length()));
    }
}
