package de.lembergmax.gitmax.ui.advanced;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.ConflictFile;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.Resolution;
import de.lembergmax.gitmax.domain.model.RunningOperation;
import de.lembergmax.gitmax.ops.OperationEntry;
import de.lembergmax.gitmax.ops.OperationTexts;
import de.lembergmax.gitmax.ui.common.Event;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Hält die Konfliktlösung eines Repos: welcher Vorgang läuft, welche Dateien noch offen sind, und führt
 * Lösen, Fortsetzen, Überspringen und Abbrechen im Hintergrund aus. Zustand nur auf dem Hauptthread.
 */
public final class ConflictsViewModel extends AndroidViewModel {

    /**
     * Zustand des Bildschirms.
     *
     * @param loading   die Daten werden gelesen
     * @param busy      eine Aktion läuft; Bedienung gesperrt
     * @param operation was Git angefangen hat
     * @param files     Dateien mit offenem Konflikt
     */
    public record UiState(
            boolean loading,
            boolean busy,
            @NonNull RunningOperation operation,
            @NonNull List<ConflictFile> files
    ) {
    }

    /** Was nach einer Aktion zu melden ist. */
    public enum NoticeKind {
        INFO,
        ERROR,
        /** Der Vorgang ist zu Ende; der Bildschirm kann sich schließen. */
        FINISHED
    }

    /**
     * Einmalige Meldung.
     *
     * @param text Text
     * @param kind Art
     */
    public record Notice(
            @NonNull String text,
            @NonNull NoticeKind kind
    ) {
    }

    private final ServiceLocator services;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<UiState> state = new MutableLiveData<>(
            new UiState(true, false, RunningOperation.NONE, List.of()));
    private final MutableLiveData<Event<Notice>> notices = new MutableLiveData<>();

    private File repo;
    private int generation;

    public ConflictsViewModel(
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

    public void reload() {
        generation += 1;
        final int current = generation;
        final UiState before = current();
        state.setValue(new UiState(true, before.busy(), before.operation(), before.files()));
        services.io().execute(() -> {
            RunningOperation operation = RunningOperation.NONE;
            List<ConflictFile> files = List.of();
            Notice failure = null;
            try {
                operation = services.advanced().runningOperation(repo);
                files = services.advanced().conflicts(repo);
            } catch (final GitFailureException failed) {
                failure = new Notice(describe(failed), NoticeKind.ERROR);
            }
            final RunningOperation loadedOperation = operation;
            final List<ConflictFile> loadedFiles = files;
            final Notice problem = failure;
            main.post(() -> {
                if (current != generation) {
                    return;
                }
                state.setValue(new UiState(false, current().busy(), loadedOperation, loadedFiles));
                if (problem != null) {
                    notices.setValue(new Event<>(problem));
                }
            });
        });
    }

    public void resolve(
            @NonNull final ConflictFile file,
            @NonNull final Resolution resolution
    ) {
        act(() -> {
            services.advanced().resolveConflict(repo, file.path(), resolution);
            return null;
        });
    }

    public void markResolved(
            @NonNull final ConflictFile file
    ) {
        act(() -> {
            services.advanced().markResolved(repo, file.path());
            return null;
        });
    }

    /** Schließt den Vorgang ab, sobald keine Konflikte mehr offen sind. */
    public void continueOperation() {
        final Optional<CommitIdentity> identity = services.identities().resolve(repo);
        if (identity.isEmpty()) {
            notices.setValue(new Event<>(new Notice(getApplication().getString(R.string.conflicts_error_identity), NoticeKind.ERROR)));
            return;
        }
        act(() -> {
            final String id = services.advanced().continueOperation(repo, identity.get());
            final RunningOperation after = services.advanced().runningOperation(repo);
            if (after != RunningOperation.NONE) {
                return new Notice(getApplication().getString(R.string.conflicts_next_step), NoticeKind.INFO);
            }
            return new Notice(id.isEmpty() ? getApplication().getString(R.string.conflicts_done)
                    : getApplication().getString(R.string.conflicts_done_commit, id), NoticeKind.FINISHED);
        });
    }

    public void skip() {
        act(() -> {
            services.advanced().skipRebase(repo);
            final RunningOperation after = services.advanced().runningOperation(repo);
            return after == RunningOperation.NONE
                    ? new Notice(getApplication().getString(R.string.conflicts_done), NoticeKind.FINISHED)
                    : new Notice(getApplication().getString(R.string.conflicts_next_step), NoticeKind.INFO);
        });
    }

    public void abort() {
        act(() -> {
            services.engine().abortMerge(repo);
            return new Notice(getApplication().getString(R.string.conflicts_aborted), NoticeKind.FINISHED);
        });
    }

    /** Eine Aktion, die eine Meldung liefern oder scheitern darf. */
    private interface Task {

        @Nullable
        Notice run() throws GitFailureException;
    }

    private void act(
            @NonNull final Task task
    ) {
        final UiState before = current();
        state.setValue(new UiState(before.loading(), true, before.operation(), before.files()));
        services.io().execute(() -> {
            Notice result = null;
            try {
                result = task.run();
            } catch (final GitFailureException failed) {
                // Konflikte im nächsten Rebase-Schritt sind kein Fehler, nur ein neuer Stand der Liste.
                result = failed.kind() == GitFailureKind.CONFLICT
                        ? new Notice(getApplication().getString(R.string.conflicts_next_step), NoticeKind.INFO)
                        : new Notice(describe(failed), NoticeKind.ERROR);
            }
            final Notice notice = result;
            main.post(() -> {
                final UiState now = current();
                state.setValue(new UiState(now.loading(), false, now.operation(), now.files()));
                if (notice != null) {
                    notices.setValue(new Event<>(notice));
                }
                reload();
            });
        });
    }

    @NonNull
    private UiState current() {
        final UiState value = state.getValue();
        return value == null ? new UiState(true, false, RunningOperation.NONE, List.of()) : value;
    }

    private String describe(
            final GitFailureException failed
    ) {
        return OperationTexts.failure(getApplication(),
                new OperationEntry.Failure(failed.kind(), failed.getMessage(), failed.paths()));
    }
}
