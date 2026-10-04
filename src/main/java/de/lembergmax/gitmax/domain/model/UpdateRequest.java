package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Objects;

/**
 * Auftrag zum Aktualisieren eines Repos (abrufen und einarbeiten).
 *
 * @param repo       Arbeitsverzeichnis
 * @param strategy   wie auseinandergelaufene Stände zusammengeführt werden
 * @param onConflict was bei Konflikten geschieht
 * @param autoStash  lokale Änderungen vorher wegstellen und danach zurückholen
 */
public record UpdateRequest(
        @NonNull File repo,
        @NonNull Strategy strategy,
        @NonNull ConflictPolicy onConflict,
        boolean autoStash
) {

    /** Zusammenführung bei Commits auf beiden Seiten. */
    public enum Strategy {
        /** Merge-Commit (Standard). */
        MERGE,
        /** Eigene Commits auf den neuen Stand aufsetzen. */
        REBASE,
        /** Nur vorspulen; bei Abweichung scheitert das Update. */
        FAST_FORWARD_ONLY
    }

    /** Umgang mit Konflikten. */
    public enum ConflictPolicy {
        /** Den Vorgang sauber zurücknehmen; das Repo bleibt unverändert (für Stapel). */
        ABORT,
        /** Das Repo im Konfliktzustand lassen, damit der Nutzer sie auflöst. */
        LEAVE
    }

    public UpdateRequest {
        Objects.requireNonNull(repo, "repo");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(onConflict, "onConflict");
    }
}
