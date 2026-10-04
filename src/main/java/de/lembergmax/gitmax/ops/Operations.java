package de.lembergmax.gitmax.ops;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.CloneRequest;
import de.lembergmax.gitmax.domain.model.PushRequest;
import de.lembergmax.gitmax.domain.model.UpdateOutcome;
import de.lembergmax.gitmax.domain.model.UpdateRequest;
import de.lembergmax.gitmax.git.GitEngine;
import de.lembergmax.gitmax.git.GitProgress;

import java.io.File;
import java.util.Objects;

/** Baut die {@link Operation}en für Klonen, Aktualisieren, Abrufen und Pushen. */
public final class Operations {

    private Operations() {
    }

    @NonNull
    public static Operation clone(
            @NonNull final CloneRequest request
    ) {
        Objects.requireNonNull(request, "request");
        return new Task(OperationKind.CLONE, request.url().displayName(), request.target(), (engine, progress) -> {
            engine.cloneRepository(request, progress);
            return OperationOutcome.CLONED;
        });
    }

    @NonNull
    public static Operation update(
            @NonNull final UpdateRequest request
    ) {
        Objects.requireNonNull(request, "request");
        return new Task(OperationKind.UPDATE, request.repo().getName(), request.repo(), (engine, progress) ->
                outcomeOf(engine.update(request, progress)));
    }

    @NonNull
    public static Operation fetch(
            @NonNull final File repo
    ) {
        Objects.requireNonNull(repo, "repo");
        return new Task(OperationKind.FETCH, repo.getName(), repo, (engine, progress) -> {
            engine.fetch(repo, progress);
            return OperationOutcome.FETCHED;
        });
    }

    @NonNull
    public static Operation push(
            @NonNull final PushRequest request
    ) {
        Objects.requireNonNull(request, "request");
        return new Task(OperationKind.PUSH, request.repo().getName(), request.repo(), (engine, progress) -> {
            engine.push(request, progress);
            return OperationOutcome.PUSHED;
        });
    }

    /** Schickt eine einzelne Referenz (Tag oder Löschen eines Remote-Branches) an ein Remote. */
    @NonNull
    public static Operation pushRef(
            @NonNull final File repo,
            @NonNull final String remote,
            @NonNull final String refSpec
    ) {
        Objects.requireNonNull(repo, "repo");
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(refSpec, "refSpec");
        return new Task(OperationKind.PUSH, repo.getName(), repo, (engine, progress) -> {
            engine.pushRef(repo, remote, refSpec, progress);
            return OperationOutcome.PUSHED;
        });
    }

    /** Ein Schritt, der mehr braucht als die Git-Engine (hier: den Anbieter für das Anlegen). */
    @FunctionalInterface
    public interface Step {

        void run(
                GitEngine engine,
                GitProgress progress
        ) throws GitFailureException;
    }

    /** Legt ein neues Repo an: beim Anbieter, im Ordner {@code target} und mit dem ersten Push. */
    @NonNull
    public static Operation createRepo(
            @NonNull final String title,
            @NonNull final File target,
            @NonNull final Step step
    ) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(step, "step");
        return new Task(OperationKind.CREATE, title, target, (engine, progress) -> {
            step.run(engine, progress);
            return OperationOutcome.CREATED;
        });
    }

    private static OperationOutcome outcomeOf(
            final UpdateOutcome outcome
    ) {
        switch (outcome) {
            case UP_TO_DATE:
                return OperationOutcome.UP_TO_DATE;
            case FAST_FORWARDED:
                return OperationOutcome.FAST_FORWARDED;
            case MERGED:
                return OperationOutcome.MERGED;
            case REBASED:
                return OperationOutcome.REBASED;
            case SKIPPED_NO_UPSTREAM:
                return OperationOutcome.SKIPPED_NO_UPSTREAM;
            case SKIPPED_DETACHED:
            default:
                return OperationOutcome.SKIPPED_DETACHED;
        }
    }

    /** Der Rumpf eines Vorgangs. */
    private interface Body {

        OperationOutcome run(
                GitEngine engine,
                GitProgress progress
        ) throws GitFailureException;
    }

    private record Task(
            OperationKind kind,
            String title,
            File directory,
            Body body
    ) implements Operation {

        @NonNull
        @Override
        public OperationOutcome execute(
                @NonNull final GitEngine engine,
                @NonNull final GitProgress progress
        ) throws GitFailureException {
            return body.run(engine, progress);
        }
    }
}
