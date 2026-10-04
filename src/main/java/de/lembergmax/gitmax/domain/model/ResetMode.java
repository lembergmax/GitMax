package de.lembergmax.gitmax.domain.model;

/** Wie weit ein Zurücksetzen reicht. */
public enum ResetMode {

    /** Nur der Branch zeigt auf den Commit; Index und Dateien bleiben. */
    SOFT,
    /** Branch und Index; die Dateien bleiben. */
    MIXED,
    /** Branch, Index und Dateien: lokale Änderungen gehen verloren. */
    HARD
}
