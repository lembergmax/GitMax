package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Änderungen eines Repos, gruppiert wie in der Commit-Ansicht. Eine Datei kann zugleich gestaged und
 * im Arbeitsverzeichnis weiter verändert sein und steht dann in beiden Listen.
 *
 * @param conflicts Dateien mit ungelösten Konflikten
 * @param staged    für den nächsten Commit vorgemerkt
 * @param unstaged  verändert oder gelöscht, noch nicht vorgemerkt
 * @param untracked neu, Git kennt sie noch nicht
 * @param inProgress ein Merge oder Rebase ist angefangen und noch nicht abgeschlossen
 */
public record WorkingTree(
        @NonNull List<ChangedFile> conflicts,
        @NonNull List<ChangedFile> staged,
        @NonNull List<ChangedFile> unstaged,
        @NonNull List<ChangedFile> untracked,
        boolean inProgress
) {

    public WorkingTree {
        conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
        staged = List.copyOf(Objects.requireNonNull(staged, "staged"));
        unstaged = List.copyOf(Objects.requireNonNull(unstaged, "unstaged"));
        untracked = List.copyOf(Objects.requireNonNull(untracked, "untracked"));
    }

    /** {@code true}, wenn nichts zu committen oder zu sichten ist. */
    public boolean isClean() {
        return conflicts.isEmpty() && staged.isEmpty() && unstaged.isEmpty() && untracked.isEmpty() && !inProgress;
    }
}
