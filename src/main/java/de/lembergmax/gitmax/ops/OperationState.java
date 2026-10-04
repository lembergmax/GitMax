package de.lembergmax.gitmax.ops;

/** Zustand eines Vorgangs. */
public enum OperationState {

    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    /** {@code true}, wenn der Vorgang nicht mehr läuft und nicht mehr laufen wird. */
    public boolean isFinished() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
