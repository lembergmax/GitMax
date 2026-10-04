package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.CloneRequest;
import de.lembergmax.gitmax.domain.model.CommitRequest;
import de.lembergmax.gitmax.domain.model.PushRequest;
import de.lembergmax.gitmax.domain.model.UpdateOutcome;
import de.lembergmax.gitmax.domain.model.UpdateRequest;
import de.lembergmax.gitmax.domain.model.WorkingTree;

import java.io.File;
import java.util.Collection;

/**
 * Die Git-Operationen der App. Alle Methoden blockieren und gehören auf einen Hintergrund-Thread. Fehler
 * kommen immer als {@link GitFailureException} mit einer Art, nie als rohe JGit-Ausnahme.
 */
public interface GitEngine {

    /** Klont ein Repo. Scheitert oder bricht der Vorgang ab, wird der angelegte Zielordner wieder entfernt. */
    void cloneRepository(
            @NonNull CloneRequest request,
            @NonNull GitProgress progress
    ) throws GitFailureException;

    /** Ruft das Remote ab und arbeitet neue Commits in den aktuellen Branch ein. */
    @NonNull
    UpdateOutcome update(
            @NonNull UpdateRequest request,
            @NonNull GitProgress progress
    ) throws GitFailureException;

    /** Ruft das Remote ab, ohne den Arbeitsstand zu verändern. */
    void fetch(
            @NonNull File repo,
            @NonNull GitProgress progress
    ) throws GitFailureException;

    /** Änderungen des Repos, gruppiert für die Commit-Ansicht. */
    @NonNull
    WorkingTree status(
            @NonNull File repo
    ) throws GitFailureException;

    /** Merkt Dateien für den nächsten Commit vor (neu, geändert oder gelöscht). */
    void stage(
            @NonNull File repo,
            @NonNull Collection<String> paths
    ) throws GitFailureException;

    /** Nimmt die Vormerkung für Dateien zurück; die Änderungen im Arbeitsverzeichnis bleiben. */
    void unstage(
            @NonNull File repo,
            @NonNull Collection<String> paths
    ) throws GitFailureException;

    /**
     * Verwirft die Änderungen an Dateien unwiderruflich: Geändertes und Gelöschtes kommt aus dem letzten
     * Commit zurück, Neues (auch Vorgemerktes) wird gelöscht.
     */
    void discard(
            @NonNull File repo,
            @NonNull Collection<String> paths
    ) throws GitFailureException;

    /**
     * Legt einen Commit aus dem Index an.
     *
     * @return gekürzte Kennung des neuen Commits
     */
    @NonNull
    String commit(
            @NonNull CommitRequest request
    ) throws GitFailureException;

    /** Schickt den aktuellen Branch zum Remote. Steht das Remote vor dem lokalen Stand, scheitert der Push. */
    void push(
            @NonNull PushRequest request,
            @NonNull GitProgress progress
    ) throws GitFailureException;

    /**
     * Schickt eine einzelne Referenz an ein Remote, z. B. einen Tag ({@code refs/tags/v1:refs/tags/v1}) oder
     * das Löschen eines Branches auf dem Remote ({@code :refs/heads/alt}).
     */
    void pushRef(
            @NonNull File repo,
            @NonNull String remote,
            @NonNull String refSpec,
            @NonNull GitProgress progress
    ) throws GitFailureException;

    /** Initialisiert und aktualisiert die Submodule des Repos (rekursiv). */
    void updateSubmodules(
            @NonNull File repo,
            @NonNull GitProgress progress
    ) throws GitFailureException;

    /**
     * Legt ein neues, leeres Repo an. Der Ordner darf nicht existieren oder muss leer sein; bei einem Fehler wird er
     * wieder entfernt.
     *
     * @param branch    Name des ersten Branches
     * @param originUrl Adresse für das Remote {@code origin}, {@code null} für ein Repo ohne Remote; Zugangsdaten in der
     *                  Adresse werden entfernt, nie in die Konfiguration geschrieben
     */
    void initRepository(
            @NonNull File repo,
            @NonNull String branch,
            @Nullable String originUrl
    ) throws GitFailureException;

    /** Bricht einen offenen Merge oder Rebase ab und stellt den Stand davor wieder her. */
    void abortMerge(
            @NonNull File repo
    ) throws GitFailureException;
}
