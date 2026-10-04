package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.BlameLine;
import de.lembergmax.gitmax.domain.model.BranchInfo;
import de.lembergmax.gitmax.domain.model.CommitDetail;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.CommitInfo;
import de.lembergmax.gitmax.domain.model.ConflictFile;
import de.lembergmax.gitmax.domain.model.FileDiff;
import de.lembergmax.gitmax.domain.model.LogQuery;
import de.lembergmax.gitmax.domain.model.RemoteInfo;
import de.lembergmax.gitmax.domain.model.RepoStats;
import de.lembergmax.gitmax.domain.model.ResetMode;
import de.lembergmax.gitmax.domain.model.Resolution;
import de.lembergmax.gitmax.domain.model.RunningOperation;
import de.lembergmax.gitmax.domain.model.StashInfo;
import de.lembergmax.gitmax.domain.model.SubmoduleInfo;
import de.lembergmax.gitmax.domain.model.TagInfo;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Die erweiterten Git-Funktionen: Verlauf, Diff, Branches, Stash, Tags und Remotes. Alle Methoden
 * blockieren und gehören auf einen Hintergrund-Thread; Fehler kommen als {@link GitFailureException}.
 * Netzwerkvorgänge (Pushen von Tags, Löschen auf dem Remote) laufen über {@link GitEngine#pushRef}.
 */
public interface GitAdvanced {

    /** Womit ein Datei-Diff der Arbeitskopie verglichen wird. */
    enum DiffBase {
        /** Arbeitsverzeichnis gegen Index: noch nicht Vorgemerktes. */
        WORKTREE_VS_INDEX,
        /** Index gegen letzten Commit: das, was der nächste Commit enthält. */
        INDEX_VS_HEAD
    }

    /** Ergebnis eines Merges. */
    enum MergeOutcome {
        ALREADY_UP_TO_DATE,
        FAST_FORWARDED,
        MERGED
    }

    /* Verlauf und Diff */

    @NonNull
    List<CommitInfo> log(
            @NonNull File repo,
            @NonNull LogQuery query
    ) throws GitFailureException;

    @NonNull
    CommitDetail commit(
            @NonNull File repo,
            @NonNull String commitId
    ) throws GitFailureException;

    /** Diff einer Datei in einem Commit gegen dessen ersten Eltern-Commit. */
    @NonNull
    FileDiff diffCommit(
            @NonNull File repo,
            @NonNull String commitId,
            @NonNull String path,
            boolean ignoreWhitespace
    ) throws GitFailureException;

    /** Diff einer Datei der Arbeitskopie; neue (nicht verfolgte) Dateien erscheinen als komplett hinzugefügt. */
    @NonNull
    FileDiff diffWorkingTree(
            @NonNull File repo,
            @NonNull String path,
            @NonNull DiffBase base,
            boolean ignoreWhitespace
    ) throws GitFailureException;

    /**
     * Wer hat jede Zeile der Datei zuletzt geändert. Zeilen, die noch in keinem Commit stehen, haben eine leere
     * Commit-Kennung ({@link BlameLine#isCommitted()} ist dann {@code false}).
     */
    @NonNull
    List<BlameLine> blame(
            @NonNull File repo,
            @NonNull String path
    ) throws GitFailureException;

    /* Branches */

    @NonNull
    List<BranchInfo> branches(
            @NonNull File repo
    ) throws GitFailureException;

    void createBranch(
            @NonNull File repo,
            @NonNull String name,
            @Nullable String startPoint,
            boolean checkout
    ) throws GitFailureException;

    void renameBranch(
            @NonNull File repo,
            @NonNull String oldName,
            @NonNull String newName
    ) throws GitFailureException;

    void deleteBranch(
            @NonNull File repo,
            @NonNull String name,
            boolean force
    ) throws GitFailureException;

    /** Checkt einen lokalen Branch aus. */
    void checkoutBranch(
            @NonNull File repo,
            @NonNull String name
    ) throws GitFailureException;

    /**
     * Checkt einen Remote-Branch (z. B. {@code origin/feature}) als lokalen Branch mit Tracking aus.
     *
     * @return Name des lokalen Branches
     */
    @NonNull
    String checkoutRemoteBranch(
            @NonNull File repo,
            @NonNull String remoteBranch
    ) throws GitFailureException;

    /** Checkt einen Commit oder Tag aus; HEAD ist danach losgelöst. */
    void checkoutCommit(
            @NonNull File repo,
            @NonNull String commitOrTag
    ) throws GitFailureException;

    /** Führt einen Branch in den aktuellen zusammen. Konflikte bleiben im Repo stehen. */
    @NonNull
    MergeOutcome merge(
            @NonNull File repo,
            @NonNull String branch
    ) throws GitFailureException;

    /** Setzt den aktuellen Branch auf einen anderen auf. Konflikte bleiben im Repo stehen. */
    void rebase(
            @NonNull File repo,
            @NonNull String onto
    ) throws GitFailureException;

    /* Stash */

    @NonNull
    List<StashInfo> stashes(
            @NonNull File repo
    ) throws GitFailureException;

    void stash(
            @NonNull File repo,
            @NonNull String message,
            boolean includeUntracked
    ) throws GitFailureException;

    /** Wendet einen Stash an; mit {@code drop} wird er danach gelöscht (pop). */
    void applyStash(
            @NonNull File repo,
            int index,
            boolean drop
    ) throws GitFailureException;

    void dropStash(
            @NonNull File repo,
            int index
    ) throws GitFailureException;

    /* Tags */

    @NonNull
    List<TagInfo> tags(
            @NonNull File repo
    ) throws GitFailureException;

    /** Legt einen Tag an; mit Nachricht annotiert, sonst leicht. */
    void createTag(
            @NonNull File repo,
            @NonNull String name,
            @Nullable String commitId,
            @Nullable String message,
            @NonNull CommitIdentity tagger
    ) throws GitFailureException;

    void deleteTag(
            @NonNull File repo,
            @NonNull String name
    ) throws GitFailureException;

    /* Remotes */

    @NonNull
    List<RemoteInfo> remotes(
            @NonNull File repo
    ) throws GitFailureException;

    void addRemote(
            @NonNull File repo,
            @NonNull String name,
            @NonNull String url
    ) throws GitFailureException;

    void setRemoteUrl(
            @NonNull File repo,
            @NonNull String name,
            @NonNull String url
    ) throws GitFailureException;

    void renameRemote(
            @NonNull File repo,
            @NonNull String oldName,
            @NonNull String newName
    ) throws GitFailureException;

    void removeRemote(
            @NonNull File repo,
            @NonNull String name
    ) throws GitFailureException;

    /* Konflikte und angefangene Vorgänge */

    /** Welchen Vorgang Git angefangen hat und noch nicht abgeschlossen hat. */
    @NonNull
    RunningOperation runningOperation(
            @NonNull File repo
    ) throws GitFailureException;

    /** Dateien mit ungelöstem Konflikt, nach Pfad sortiert. */
    @NonNull
    List<ConflictFile> conflicts(
            @NonNull File repo
    ) throws GitFailureException;

    /** Löst den Konflikt einer Datei mit einer Seite oder beiden und merkt sie vor. */
    void resolveConflict(
            @NonNull File repo,
            @NonNull String path,
            @NonNull Resolution resolution
    ) throws GitFailureException;

    /** Merkt eine von Hand bearbeitete Datei als gelöst vor. */
    void markResolved(
            @NonNull File repo,
            @NonNull String path
    ) throws GitFailureException;

    /**
     * Schließt Merge, Cherry-pick, Revert oder den aktuellen Rebase-Schritt ab. Scheitert mit
     * {@code CONFLICT}, solange Konflikte offen sind.
     *
     * @return gekürzte Kennung des neuen Commits, leer wenn nur ein Rebase-Schritt weiterlief
     */
    @NonNull
    String continueOperation(
            @NonNull File repo,
            @NonNull CommitIdentity committer
    ) throws GitFailureException;

    /** Überspringt den aktuellen Commit eines laufenden Rebases. */
    void skipRebase(
            @NonNull File repo
    ) throws GitFailureException;

    /* Verlauf umschreiben */

    void reset(
            @NonNull File repo,
            @NonNull String commitId,
            @NonNull ResetMode mode
    ) throws GitFailureException;

    /** Legt einen Commit an, der die Änderungen eines früheren aufhebt. */
    void revert(
            @NonNull File repo,
            @NonNull String commitId
    ) throws GitFailureException;

    /** Übernimmt die Änderungen eines Commits auf den aktuellen Branch. */
    void cherryPick(
            @NonNull File repo,
            @NonNull String commitId
    ) throws GitFailureException;

    /* Wartung */

    /** Nicht verfolgte, nicht ignorierte Dateien und Ordner, die {@link #clean} entfernen würde. */
    @NonNull
    List<String> cleanPreview(
            @NonNull File repo
    ) throws GitFailureException;

    /** Löscht die genannten nicht verfolgten Dateien und Ordner unwiderruflich. */
    void clean(
            @NonNull File repo,
            @NonNull Collection<String> paths
    ) throws GitFailureException;

    @NonNull
    RepoStats stats(
            @NonNull File repo
    ) throws GitFailureException;

    /** Verdichtet die Git-Daten (Referenzen und Objekte packen, Überflüssiges entfernen). */
    void collectGarbage(
            @NonNull File repo
    ) throws GitFailureException;

    /** Inhalt von {@code .git/info/exclude}; leer, wenn es die Datei nicht gibt. */
    @NonNull
    String readExclude(
            @NonNull File repo
    ) throws GitFailureException;

    void writeExclude(
            @NonNull File repo,
            @NonNull String text
    ) throws GitFailureException;

    @NonNull
    List<SubmoduleInfo> submodules(
            @NonNull File repo
    ) throws GitFailureException;

    /** Ob {@code fetch.prune} gesetzt ist; Standard dieser App ist {@code true}. */
    boolean pruneOnFetch(
            @NonNull File repo
    ) throws GitFailureException;

    void setPruneOnFetch(
            @NonNull File repo,
            boolean prune
    ) throws GitFailureException;
}
