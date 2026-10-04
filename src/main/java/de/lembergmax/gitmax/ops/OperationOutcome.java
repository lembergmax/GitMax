package de.lembergmax.gitmax.ops;

/** Ergebnis eines erfolgreichen Vorgangs; die Oberfläche macht daraus Text. */
public enum OperationOutcome {

    CLONED,
    UP_TO_DATE,
    FAST_FORWARDED,
    MERGED,
    REBASED,
    SKIPPED_NO_UPSTREAM,
    SKIPPED_DETACHED,
    FETCHED,
    PUSHED,
    CREATED;

    /** {@code true}, wenn der Vorgang sich nicht ausgeführt hat (kein Upstream, losgelöster HEAD). */
    public boolean isSkipped() {
        return this == SKIPPED_NO_UPSTREAM || this == SKIPPED_DETACHED;
    }
}
