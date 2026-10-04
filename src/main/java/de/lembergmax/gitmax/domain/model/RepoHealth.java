package de.lembergmax.gitmax.domain.model;

/**
 * Der eine Zustand, der ein Repo in Liste und Kieselstein vertritt. Mehrere Tatsachen können
 * zugleich gelten (geändert und voraus); es zählt die Handlung, die als Nächstes ansteht.
 */
public enum RepoHealth {

    /** Ungelöste Merge-Konflikte: zuerst auflösen. */
    CONFLICT,
    /** Lokale Änderungen: zuerst committen. */
    CHANGED,
    /** Lokal und remote sind auseinandergelaufen. */
    DIVERGED,
    /** Das Remote hat neue Commits: aktualisieren. */
    BEHIND,
    /** Lokale Commits sind noch nicht gepusht. */
    AHEAD,
    /** Nichts zu tun. */
    CLEAN
}
