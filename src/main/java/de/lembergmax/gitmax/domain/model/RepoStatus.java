package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Momentaufnahme eines lokalen Repos.
 *
 * @param branch      Name des ausgecheckten Branches; bei losgelöstem HEAD die gekürzte Commit-Kennung
 * @param detached    HEAD zeigt auf einen Commit statt auf einen Branch
 * @param hasUpstream der Branch verfolgt einen Remote-Branch
 * @param ahead       lokale Commits, die der Remote-Branch nicht hat (0 ohne Upstream)
 * @param behind      Commits des Remote-Branches, die lokal fehlen (0 ohne Upstream)
 * @param changed     Anzahl geänderter, neuer, gelöschter oder in Konflikt stehender Dateien;
 *                    {@link #CHANGES_UNKNOWN}, solange die Prüfung noch läuft
 * @param conflicted  ungelöste Konflikte vorhanden
 */
public record RepoStatus(
        @NonNull String branch,
        boolean detached,
        boolean hasUpstream,
        int ahead,
        int behind,
        int changed,
        boolean conflicted
) {

    /** Wert von {@link #changed()}, solange die (langsame) Dateiprüfung noch nicht lief. */
    public static final int CHANGES_UNKNOWN = -1;

    public RepoStatus {
        Objects.requireNonNull(branch, "branch");
    }

    /** Der Zustand, der das Repo in der Liste vertritt. */
    @NonNull
    public RepoHealth health() {
        if (conflicted) {
            return RepoHealth.CONFLICT;
        }
        if (changed > 0) {
            return RepoHealth.CHANGED;
        }
        if (ahead > 0 && behind > 0) {
            return RepoHealth.DIVERGED;
        }
        if (behind > 0) {
            return RepoHealth.BEHIND;
        }
        if (ahead > 0) {
            return RepoHealth.AHEAD;
        }
        return RepoHealth.CLEAN;
    }

    /** {@code true}, wenn die Dateiprüfung schon ein Ergebnis hat. */
    public boolean changesKnown() {
        return changed != CHANGES_UNKNOWN;
    }

    /** Dieselbe Momentaufnahme mit dem Ergebnis der Dateiprüfung. */
    @NonNull
    public RepoStatus withChanges(
            final int newChanged,
            final boolean newConflicted
    ) {
        return new RepoStatus(branch, detached, hasUpstream, ahead, behind, newChanged, newConflicted);
    }
}
