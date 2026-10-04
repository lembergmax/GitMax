package de.lembergmax.gitmax.ui.advanced;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.RepoStats;
import de.lembergmax.gitmax.domain.model.SubmoduleInfo;
import de.lembergmax.gitmax.domain.model.UpdateRequest;
import de.lembergmax.gitmax.git.GitProgress;
import de.lembergmax.gitmax.ops.OperationEntry;
import de.lembergmax.gitmax.ops.OperationTexts;
import de.lembergmax.gitmax.storage.RepoFiles;
import de.lembergmax.gitmax.ui.common.Event;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Hält die Einstellungen und Wartungswerkzeuge eines Repos: Identität, Update-Strategie, Bereinigung beim
 * Abrufen, Ausschlüsse, Aufräumen, Verdichten und Submodule. Zustand nur auf dem Hauptthread.
 */
public final class RepoToolsViewModel extends AndroidViewModel {

    /**
     * Alles, was die Liste zeigt.
     *
     * @param identity   gültige Identität für dieses Repo oder {@code null}, wenn keine bekannt ist
     * @param identityChosen die Identität wurde für dieses Repo gewählt, sie stammt nicht vom Konto
     * @param strategy   Update-Strategie
     * @param prune      beim Abrufen aufräumen
     * @param stats      Größe der Git-Daten
     * @param submodules Submodule
     */
    public record Data(
            @Nullable CommitIdentity identity,
            boolean identityChosen,
            @NonNull UpdateRequest.Strategy strategy,
            boolean prune,
            @NonNull RepoStats stats,
            @NonNull List<SubmoduleInfo> submodules
    ) {
    }

    /**
     * Zustand des Bildschirms.
     *
     * @param loading die Daten werden gelesen
     * @param busy    eine Aktion läuft
     * @param data    die Werte, solange kein Lesefehler auftrat
     */
    public record UiState(
            boolean loading,
            boolean busy,
            @Nullable Data data
    ) {
    }

    /**
     * Einmalige Meldung.
     *
     * @param text  Text
     * @param error ein Fehler, kein Erfolg
     */
    public record Notice(
            @NonNull String text,
            boolean error
    ) {
    }

    private final ServiceLocator services;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<UiState> state = new MutableLiveData<>(new UiState(true, false, null));
    private final MutableLiveData<Event<Notice>> notices = new MutableLiveData<>();
    private final MutableLiveData<Event<List<String>>> cleanCandidates = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> excludeText = new MutableLiveData<>();

    private File repo;
    private int generation;

    public RepoToolsViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
    }

    /** Legt das Repo fest; ein zweiter Aufruf ändert nichts. */
    public void init(
            @NonNull final String directory
    ) {
        if (repo != null) {
            return;
        }
        repo = new File(directory);
        reload();
    }

    @NonNull
    public File repo() {
        return repo;
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }

    @NonNull
    public LiveData<Event<Notice>> notices() {
        return notices;
    }

    /** Dateien, die „Aufräumen“ löschen würde; die Oberfläche fragt vor dem Löschen nach. */
    @NonNull
    public LiveData<Event<List<String>>> cleanCandidates() {
        return cleanCandidates;
    }

    /** Inhalt der persönlichen Ausschlüsse, den die Oberfläche zum Bearbeiten anbietet. */
    @NonNull
    public LiveData<Event<String>> excludeText() {
        return excludeText;
    }

    public void reload() {
        generation += 1;
        final int current = generation;
        final UiState before = current();
        state.setValue(new UiState(true, before.busy(), before.data()));
        services.io().execute(() -> {
            Data loaded = null;
            Notice failure = null;
            try {
                loaded = new Data(
                        services.identities().resolve(repo).orElse(null),
                        services.repoSettings().identity(repo).isPresent(),
                        services.repoSettings().updateStrategy(repo),
                        services.advanced().pruneOnFetch(repo),
                        services.advanced().stats(repo),
                        services.advanced().submodules(repo));
            } catch (final GitFailureException failed) {
                failure = new Notice(describe(failed), true);
            }
            final Data data = loaded;
            final Notice problem = failure;
            main.post(() -> {
                if (current != generation) {
                    return;
                }
                state.setValue(new UiState(false, current().busy(), data));
                if (problem != null) {
                    notices.setValue(new Event<>(problem));
                }
            });
        });
    }

    public void chooseIdentity(
            @NonNull final CommitIdentity identity
    ) {
        services.identities().choose(repo, identity);
        reload();
    }

    public void chooseStrategy(
            @NonNull final UpdateRequest.Strategy strategy
    ) {
        services.repoSettings().setUpdateStrategy(repo, strategy);
        reload();
    }

    public void setPrune(
            final boolean prune
    ) {
        act(R.string.tools_saved, () -> services.advanced().setPruneOnFetch(repo, prune));
    }

    /** Liest die persönlichen Ausschlüsse und meldet sie über {@link #excludeText()}. */
    public void loadExclude() {
        act(0, () -> {
            final String text = services.advanced().readExclude(repo);
            main.post(() -> excludeText.setValue(new Event<>(text)));
        });
    }

    public void saveExclude(
            @NonNull final String text
    ) {
        act(R.string.tools_saved, () -> services.advanced().writeExclude(repo, text));
    }

    /** Legt {@code .gitignore} an, falls es sie nicht gibt; danach kann sie bearbeitet werden. */
    public void ensureGitignore(
            @NonNull final Runnable afterwards
    ) {
        services.io().execute(() -> {
            try {
                new RepoFiles(repo).createFile("", ".gitignore");
            } catch (final RepoFiles.FilesException failed) {
                // Existiert die Datei schon, ist das gewünscht; nur andere Gründe sind ein Fehler.
                if (failed.reason() != RepoFiles.Reason.EXISTS) {
                    main.post(() -> notices.setValue(new Event<>(new Notice(
                            getApplication().getString(R.string.files_error_io), true))));
                    return;
                }
            }
            main.post(afterwards);
        });
    }

    /** Ermittelt, was „Aufräumen“ löschen würde, und meldet es über {@link #cleanCandidates()}. */
    public void prepareClean() {
        act(0, () -> {
            final List<String> paths = services.advanced().cleanPreview(repo);
            main.post(() -> {
                if (paths.isEmpty()) {
                    notices.setValue(new Event<>(new Notice(getApplication().getString(R.string.tools_clean_nothing), false)));
                } else {
                    cleanCandidates.setValue(new Event<>(paths));
                }
            });
        });
    }

    public void clean(
            @NonNull final Collection<String> paths
    ) {
        act(R.string.tools_cleaned, () -> services.advanced().clean(repo, paths));
    }

    public void collectGarbage() {
        final RepoStats before = stats();
        services.io().execute(() -> {
            setBusy(true);
            Notice result;
            try {
                services.advanced().collectGarbage(repo);
                final RepoStats after = services.advanced().stats(repo);
                result = new Notice(getApplication().getString(R.string.tools_gc_done,
                        Formatter.formatShortFileSize(getApplication(), after.totalBytes()),
                        Formatter.formatShortFileSize(getApplication(), before.totalBytes())), false);
            } catch (final GitFailureException failed) {
                result = new Notice(describe(failed), true);
            }
            finish(result);
        });
    }

    public void updateSubmodules() {
        act(R.string.tools_submodules_updated, () -> services.engine().updateSubmodules(repo, GitProgress.NONE));
    }

    /** Eine Aktion, die scheitern darf. */
    private interface Task {

        void run() throws GitFailureException;
    }

    private void act(
            @StringRes final int success,
            @NonNull final Task task
    ) {
        services.io().execute(() -> {
            setBusy(true);
            Notice result = null;
            try {
                task.run();
                if (success != 0) {
                    result = new Notice(getApplication().getString(success), false);
                }
            } catch (final GitFailureException failed) {
                result = new Notice(describe(failed), true);
            }
            finish(result);
        });
    }

    private void setBusy(
            final boolean busy
    ) {
        main.post(() -> {
            final UiState now = current();
            state.setValue(new UiState(now.loading(), busy, now.data()));
        });
    }

    private void finish(
            @Nullable final Notice result
    ) {
        main.post(() -> {
            final UiState now = current();
            state.setValue(new UiState(now.loading(), false, now.data()));
            if (result != null) {
                notices.setValue(new Event<>(result));
            }
            reload();
        });
    }

    @NonNull
    private RepoStats stats() {
        final Data data = current().data();
        return data == null ? new RepoStats(0, 0, 0, 0) : data.stats();
    }

    @NonNull
    private UiState current() {
        final UiState value = state.getValue();
        return value == null ? new UiState(true, false, null) : value;
    }

    private String describe(
            final GitFailureException failed
    ) {
        return OperationTexts.failure(getApplication(),
                new OperationEntry.Failure(failed.kind(), failed.getMessage(), failed.paths()));
    }
}
