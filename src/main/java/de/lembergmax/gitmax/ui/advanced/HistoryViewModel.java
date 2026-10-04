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
import de.lembergmax.gitmax.domain.model.CommitInfo;
import de.lembergmax.gitmax.domain.model.LogQuery;
import de.lembergmax.gitmax.ops.OperationEntry;
import de.lembergmax.gitmax.ops.OperationTexts;
import de.lembergmax.gitmax.ui.common.Event;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Der Verlauf eines Repos, seitenweise geladen, mit Filtern nach Text, Autor, Pfad und Branches. */
public final class HistoryViewModel extends AndroidViewModel {

    /**
     * Zustand des Bildschirms.
     *
     * @param loading     eine Seite wird geladen
     * @param commits     bisher geladene Commits
     * @param hasMore     es kann weitere Seiten geben
     * @param allBranches Verlauf aller Branches statt nur des aktuellen
     * @param author      Autorenfilter
     * @param path        Pfadfilter
     */
    public record UiState(
            boolean loading,
            @NonNull List<CommitInfo> commits,
            boolean hasMore,
            boolean allBranches,
            @NonNull String author,
            @NonNull String path
    ) {
    }

    private final ServiceLocator services;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<UiState> state = new MutableLiveData<>(new UiState(true, List.of(), false, false, "", ""));
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();

    private File repo;
    private String text = "";
    private String author = "";
    private String path = "";
    private boolean allBranches;
    private LogQuery lastQuery = LogQuery.head();
    private List<CommitInfo> commits = new ArrayList<>();
    private boolean hasMore;
    private boolean loading;
    private int generation;

    public HistoryViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
    }

    /** Legt das Repo fest und lädt die erste Seite; ein zweiter Aufruf ändert nichts. */
    public void init(
            @NonNull final String directory,
            @Nullable final String initialPath
    ) {
        if (repo != null) {
            return;
        }
        repo = new File(directory);
        path = initialPath == null ? "" : initialPath;
        reload();
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }

    @NonNull
    public LiveData<Event<String>> messages() {
        return messages;
    }

    @NonNull
    public File repo() {
        return repo;
    }

    public void setText(
            @NonNull final String newText
    ) {
        if (!text.equals(newText.strip())) {
            text = newText.strip();
            reload();
        }
    }

    public void setAllBranches(
            final boolean value
    ) {
        allBranches = value;
        reload();
    }

    public void setFilter(
            @NonNull final String newAuthor,
            @NonNull final String newPath
    ) {
        author = newAuthor.strip();
        path = newPath.strip();
        reload();
    }

    /** Lädt von vorn mit den aktuellen Filtern. */
    public void reload() {
        commits = new ArrayList<>();
        hasMore = false;
        lastQuery = new LogQuery(null, allBranches, author, text, path, 0, LogQuery.PAGE_SIZE);
        loadPage(lastQuery);
    }

    /** Lädt die nächste Seite, wenn es eine gibt und nicht schon geladen wird. */
    public void loadMore() {
        if (hasMore && !loading) {
            lastQuery = lastQuery.nextPage();
            loadPage(lastQuery);
        }
    }

    private void loadPage(
            @NonNull final LogQuery query
    ) {
        generation += 1;
        final int current = generation;
        loading = true;
        publish();
        services.io().execute(() -> {
            List<CommitInfo> page = List.of();
            String failure = null;
            try {
                page = services.advanced().log(repo, query);
            } catch (final GitFailureException failed) {
                failure = OperationTexts.failure(getApplication(),
                        new OperationEntry.Failure(failed.kind(), failed.getMessage(), failed.paths()));
            }
            final List<CommitInfo> loaded = page;
            final String problem = failure;
            main.post(() -> {
                if (current != generation) {
                    return;
                }
                commits.addAll(loaded);
                hasMore = loaded.size() >= query.limit();
                loading = false;
                publish();
                if (problem != null) {
                    messages.setValue(new Event<>(problem));
                }
            });
        });
    }

    private void publish() {
        state.setValue(new UiState(loading, new ArrayList<>(commits), hasMore, allBranches, author, path));
    }
}
