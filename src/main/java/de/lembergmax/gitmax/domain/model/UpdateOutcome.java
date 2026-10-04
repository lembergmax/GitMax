package de.lembergmax.gitmax.domain.model;

/**
 * Ergebnis eines erfolgreich abgeschlossenen Updates. Konflikte sind kein Ergebnis, sondern eine
 * {@link de.lembergmax.gitmax.domain.GitFailureException} der Art {@link GitFailureKind#CONFLICT}.
 */
public enum UpdateOutcome {

    /** Es gab nichts Neues. */
    UP_TO_DATE,
    /** Der lokale Branch wurde vorgespult. */
    FAST_FORWARDED,
    /** Lokale und entfernte Commits wurden per Merge zusammengeführt. */
    MERGED,
    /** Lokale Commits wurden auf den neuen Stand aufgesetzt. */
    REBASED,
    /** Übersprungen: der Branch hat keinen Upstream. */
    SKIPPED_NO_UPSTREAM,
    /** Übersprungen: HEAD zeigt auf einen Commit statt auf einen Branch. */
    SKIPPED_DETACHED
}
