package de.lembergmax.gitmax.domain.model;

/** Wie ein Konflikt in einer Datei aufgelöst wird. */
public enum Resolution {

    /** Die Fassung der eigenen Seite (HEAD) übernehmen. */
    OURS,
    /** Die Fassung der anderen Seite übernehmen. */
    THEIRS,
    /** Beide Fassungen behalten: erst die eigene, dann die andere. */
    BOTH
}
