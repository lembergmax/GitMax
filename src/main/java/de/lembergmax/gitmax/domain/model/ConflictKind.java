package de.lembergmax.gitmax.domain.model;

/** Art eines Konflikts, wie Git ihn nach einem Merge, Rebase oder Cherry-pick meldet. */
public enum ConflictKind {

    /** Beide Seiten haben dieselbe Datei geändert. */
    BOTH_MODIFIED,
    /** Beide Seiten haben dieselbe Datei neu angelegt. */
    BOTH_ADDED,
    /** Beide Seiten haben die Datei gelöscht. */
    BOTH_DELETED,
    /** Unsere Seite hat sie gelöscht, die andere geändert. */
    DELETED_BY_US,
    /** Die andere Seite hat sie gelöscht, unsere geändert. */
    DELETED_BY_THEM,
    /** Nur unsere Seite hat die Datei angelegt. */
    ADDED_BY_US,
    /** Nur die andere Seite hat die Datei angelegt. */
    ADDED_BY_THEM
}
