package de.lembergmax.gitmax.ui.advanced;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.ops.OperationEntry;
import de.lembergmax.gitmax.ops.OperationTexts;
import de.lembergmax.gitmax.ui.common.Event;

import java.io.File;
import java.util.List;

/**
 * Hält eine einfache Liste (Branches, Stash, Tags, Remotes): lädt über die {@link ListSource}, führt
 * Aktionen im Hintergrund aus und meldet Ergebnisse und Fehler. Zustand nur auf dem Hauptthread.
 */
public final class AdvancedListViewModel extends AndroidViewModel {

    /** Art einer Meldung. */
    public enum NoticeKind {
        INFO,
        ERROR,
        /** Konflikt: das Repo steht im Zwischenzustand und braucht den Repo-Bildschirm. */
        CONFLICT,
        /** Der Branch ist nicht eingearbeitet; Löschen braucht Bestätigung. */
        NOT_MERGED
    }

    /**
     * Einmalige Meldung an den Bildschirm.
     *
     * @param text      Text
     * @param kind      Art
     * @param row       Zeile, auf die sich eine Rückfrage bezieht (bei {@link NoticeKind#NOT_MERGED})
     * @param actionId  Aktion, die nach Bestätigung erneut läuft
     */
    public record Notice(
            @NonNull String text,
            @NonNull NoticeKind kind,
            @Nullable SimpleRow row,
            @Nullable String actionId
    ) {
    }

    /**
     * Zustand des Bildschirms.
     *
     * @param loading die Liste wird gelesen
     * @param rows    Zeilen
     */
    public record UiState(
            boolean loading,
            @NonNull List<SimpleRow> rows
    ) {
    }

    private final ServiceLocator services;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<UiState> state = new MutableLiveData<>(new UiState(true, List.of()));
    private final MutableLiveData<Event<Notice>> notices = new MutableLiveData<>();

    private ListSource source;
    private String directory = "";
    private int generation;

    public AdvancedListViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
    }

    /** Legt die Liste fest; ein zweiter Aufruf ändert nichts. */
    public void init(
            @NonNull final String kind,
            @NonNull final String directory
    ) {
        if (source != null) {
            return;
        }
        this.directory = directory;
        source = ListSources.create(getApplication(), services, kind, new File(directory));
        reload();
    }

    @NonNull
    public ListSource source() {
        return source;
    }

    /** Pfad des Repos, dessen Liste gezeigt wird. */
    @NonNull
    public String directory() {
        return directory;
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
        final List<SimpleRow> before = state.getValue() == null ? List.of() : state.getValue().rows();
        state.setValue(new UiState(true, before));
        services.io().execute(() -> {
            List<SimpleRow> rows = List.of();
            Notice failure = null;
            try {
                rows = source.load();
            } catch (final GitFailureException failed) {
                failure = noticeOf(failed, null, null);
            }
            final List<SimpleRow> loaded = rows;
            final Notice problem = failure;
            main.post(() -> {
                if (current != generation) {
                    return;
                }
                state.setValue(new UiState(false, loaded));
                if (problem != null) {
                    notices.setValue(new Event<>(problem));
                }
            });
        });
    }

    public void create(
            @NonNull final ListSource.Values values
    ) {
        run(null, null, () -> {
            source.create(values);
            return 0;
        });
    }

    public void perform(
            @NonNull final SimpleRow row,
            @NonNull final String actionId,
            @NonNull final ListSource.Values values
    ) {
        run(row, actionId, () -> source.perform(row, actionId, values));
    }

    /** Eine Aktion, die eine Erfolgsmeldung liefert oder scheitert. */
    private interface Task {

        int run() throws GitFailureException;
    }

    private void run(
            @Nullable final SimpleRow row,
            @Nullable final String actionId,
            @NonNull final Task task
    ) {
        services.io().execute(() -> {
            int message = 0;
            Notice failure = null;
            try {
                message = task.run();
            } catch (final GitFailureException failed) {
                failure = noticeOf(failed, row, actionId);
            }
            final int success = message;
            final Notice problem = failure;
            main.post(() -> {
                if (problem != null) {
                    notices.setValue(new Event<>(problem));
                } else if (success != 0) {
                    notices.setValue(new Event<>(new Notice(getApplication().getString(success), NoticeKind.INFO, null, null)));
                }
                reload();
            });
        });
    }

    private Notice noticeOf(
            final GitFailureException failed,
            @Nullable final SimpleRow row,
            @Nullable final String actionId
    ) {
        final String text = OperationTexts.failure(getApplication(),
                new OperationEntry.Failure(failed.kind(), failed.getMessage(), failed.paths()));
        final NoticeKind kind = failed.kind() == GitFailureKind.CONFLICT ? NoticeKind.CONFLICT
                : failed.kind() == GitFailureKind.NOT_MERGED ? NoticeKind.NOT_MERGED : NoticeKind.ERROR;
        return new Notice(text, kind, row, actionId);
    }
}
