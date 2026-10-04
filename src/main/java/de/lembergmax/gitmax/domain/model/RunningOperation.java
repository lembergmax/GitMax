package de.lembergmax.gitmax.domain.model;

/** Ein Vorgang, den Git angefangen und noch nicht abgeschlossen hat (meist wegen Konflikten). */
public enum RunningOperation {

    /** Es läuft nichts. */
    NONE,
    /** Ein Merge. */
    MERGE,
    /** Ein Rebase. */
    REBASE,
    /** Ein Cherry-pick. */
    CHERRY_PICK,
    /** Ein Revert. */
    REVERT
}
