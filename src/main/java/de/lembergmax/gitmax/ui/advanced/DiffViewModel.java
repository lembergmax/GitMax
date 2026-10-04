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
import de.lembergmax.gitmax.domain.model.FileDiff;
import de.lembergmax.gitmax.git.GitAdvanced;
import de.lembergmax.gitmax.ops.OperationEntry;
import de.lembergmax.gitmax.ops.OperationTexts;
import de.lembergmax.gitmax.ui.common.Event;

import java.io.File;

/** Lädt den Diff einer Datei: aus einem Commit oder aus der Arbeitskopie (vorgemerkt oder nicht). */
public final class DiffViewModel extends AndroidViewModel {

    /** Woher der Diff kommt. */
    public enum Mode {
        /** Änderung einer Datei in einem Commit. */
        COMMIT,
        /** Noch nicht Vorgemerktes. */
        WORKTREE,
        /** Vorgemerktes, das der nächste Commit enthält. */
        STAGED
    }

    private final ServiceLocator services;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<FileDiff> diff = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();

    private File repo;
    private Mode mode;
    private String commitId;
    private String path = "";
    private boolean ignoreWhitespace;
    private int generation;

    public DiffViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
    }

    /** Legt den Diff fest und lädt ihn; ein zweiter Aufruf ändert nichts. */
    public void init(
            @NonNull final String directory,
            @NonNull final Mode newMode,
            @Nullable final String commit,
            @NonNull final String filePath
    ) {
        if (repo != null) {
            return;
        }
        repo = new File(directory);
        mode = newMode;
        commitId = commit;
        path = filePath;
        load();
    }

    @NonNull
    public LiveData<FileDiff> diff() {
        return diff;
    }

    @NonNull
    public LiveData<Boolean> loading() {
        return loading;
    }

    @NonNull
    public LiveData<Event<String>> messages() {
        return messages;
    }

    public boolean ignoreWhitespace() {
        return ignoreWhitespace;
    }

    public void setIgnoreWhitespace(
            final boolean value
    ) {
        ignoreWhitespace = value;
        load();
    }

    private void load() {
        generation += 1;
        final int current = generation;
        loading.setValue(true);
        services.io().execute(() -> {
            FileDiff loaded = null;
            String failure = null;
            try {
                switch (mode) {
                    case COMMIT:
                        loaded = services.advanced().diffCommit(repo, commitId, path, ignoreWhitespace);
                        break;
                    case STAGED:
                        loaded = services.advanced().diffWorkingTree(repo, path, GitAdvanced.DiffBase.INDEX_VS_HEAD, ignoreWhitespace);
                        break;
                    case WORKTREE:
                    default:
                        loaded = services.advanced().diffWorkingTree(repo, path, GitAdvanced.DiffBase.WORKTREE_VS_INDEX, ignoreWhitespace);
                        break;
                }
            } catch (final GitFailureException failed) {
                failure = OperationTexts.failure(getApplication(),
                        new OperationEntry.Failure(failed.kind(), failed.getMessage(), failed.paths()));
            }
            final FileDiff result = loaded;
            final String problem = failure;
            main.post(() -> {
                if (current != generation) {
                    return;
                }
                diff.setValue(result);
                loading.setValue(false);
                if (problem != null) {
                    messages.setValue(new Event<>(problem));
                }
            });
        });
    }
}
