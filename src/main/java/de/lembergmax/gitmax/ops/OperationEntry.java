package de.lembergmax.gitmax.ops;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.git.GitProgress;

import java.util.List;
import java.util.Objects;

/**
 * Unveränderlicher Stand eines Vorgangs für die Oberfläche.
 *
 * @param id         laufende Nummer, auch über App-Starts hinweg eindeutig
 * @param kind       Art des Vorgangs
 * @param title      Anzeigename des Repos
 * @param directory  Ordner des Repos
 * @param state      Zustand
 * @param progress   letzter Fortschritt, nur während des Laufs
 * @param outcome    Ergebnis, nur bei {@link OperationState#SUCCEEDED}
 * @param failure    Fehler, nur bei {@link OperationState#FAILED} und {@link OperationState#CANCELLED}
 * @param enqueuedAt Zeitpunkt des Einreihens in Millisekunden
 * @param startedAt  Zeitpunkt des Starts oder 0
 * @param finishedAt Zeitpunkt des Endes oder 0
 */
public record OperationEntry(
        long id,
        @NonNull OperationKind kind,
        @NonNull String title,
        @NonNull String directory,
        @NonNull OperationState state,
        @Nullable Progress progress,
        @Nullable OperationOutcome outcome,
        @Nullable Failure failure,
        long enqueuedAt,
        long startedAt,
        long finishedAt
) {

    /**
     * Fortschritt eines laufenden Vorgangs.
     *
     * @param phase    aktueller Abschnitt
     * @param fraction Anteil 0 bis 1 oder {@link GitProgress#UNKNOWN}
     */
    public record Progress(
            @NonNull GitProgress.Phase phase,
            float fraction
    ) {

        public Progress {
            Objects.requireNonNull(phase, "phase");
        }
    }

    /**
     * Grund, warum ein Vorgang nicht erfolgreich war.
     *
     * @param kind    Art des Fehlers
     * @param message technische Meldung, nie mit Token
     * @param paths   betroffene Dateien (Konflikte, ungültige Namen)
     */
    public record Failure(
            @NonNull GitFailureKind kind,
            @NonNull String message,
            @NonNull List<String> paths
    ) {

        public Failure {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(message, "message");
            paths = List.copyOf(Objects.requireNonNull(paths, "paths"));
        }
    }

    public OperationEntry {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(state, "state");
    }

    @NonNull
    OperationEntry running(
            final long now
    ) {
        return new OperationEntry(id, kind, title, directory, OperationState.RUNNING, null, null, null,
                enqueuedAt, now, 0L);
    }

    @NonNull
    OperationEntry withProgress(
            @NonNull final Progress newProgress
    ) {
        return new OperationEntry(id, kind, title, directory, state, newProgress, outcome, failure,
                enqueuedAt, startedAt, finishedAt);
    }

    @NonNull
    OperationEntry succeeded(
            @NonNull final OperationOutcome result,
            final long now
    ) {
        return new OperationEntry(id, kind, title, directory, OperationState.SUCCEEDED, null, result, null,
                enqueuedAt, startedAt, now);
    }

    @NonNull
    OperationEntry ended(
            @NonNull final OperationState endState,
            @NonNull final Failure reason,
            final long now
    ) {
        return new OperationEntry(id, kind, title, directory, endState, null, null, reason,
                enqueuedAt, startedAt, now);
    }
}
