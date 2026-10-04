package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Abfrage des Verlaufs.
 *
 * @param ref    Branch, Tag oder Commit als Startpunkt; {@code null} für HEAD
 * @param all    alle Branches und Tags statt nur eines Startpunkts
 * @param author Filter auf Name oder E-Mail des Autors, leer für keinen
 * @param text   Filter auf die Nachricht, leer für keinen
 * @param path   Filter auf einen Pfad im Repo, leer für keinen
 * @param skip   so viele Commits überspringen (seitenweises Laden)
 * @param limit  höchstens so viele Commits liefern
 */
public record LogQuery(
        @Nullable String ref,
        boolean all,
        @NonNull String author,
        @NonNull String text,
        @NonNull String path,
        int skip,
        int limit
) {

    /** Seitengröße der Verlaufsliste. */
    public static final int PAGE_SIZE = 50;

    public LogQuery {
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(path, "path");
    }

    /** Die erste Seite des Verlaufs von HEAD ohne Filter. */
    @NonNull
    public static LogQuery head() {
        return new LogQuery(null, false, "", "", "", 0, PAGE_SIZE);
    }

    /** Die nächste Seite derselben Abfrage. */
    @NonNull
    public LogQuery nextPage() {
        return new LogQuery(ref, all, author, text, path, skip + limit, limit);
    }
}
