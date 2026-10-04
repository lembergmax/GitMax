package de.lembergmax.gitmax.ui.files;

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
import de.lembergmax.gitmax.domain.FileStatusIndex;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.ChangedFile;
import de.lembergmax.gitmax.storage.RepoFiles;
import de.lembergmax.gitmax.ui.common.Event;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Inhalt eines Ordners im Repo mit Git-Markierungen, dazu Anlegen, Umbenennen und Löschen. Zustand nur auf
 * dem Hauptthread; Dateiarbeit läuft im Hintergrund.
 */
public final class FilesViewModel extends AndroidViewModel {

    /**
     * Eine Zeile der Liste.
     *
     * @param entry      Datei oder Ordner
     * @param kind       Art der Änderung bei Dateien, sonst {@code null}
     * @param hasChanges bei Ordnern: darunter ist etwas geändert
     */
    public record Row(
            @NonNull RepoFiles.Entry entry,
            @Nullable ChangedFile.Kind kind,
            boolean hasChanges
    ) {
    }

    /**
     * Was der Bildschirm zeigt.
     *
     * @param loading die Liste wird gelesen
     * @param rows    Zeilen nach Filter
     * @param empty   der Ordner ist leer (unabhängig vom Filter)
     */
    public record UiState(
            boolean loading,
            @NonNull List<Row> rows,
            boolean empty
    ) {
    }

    private final ServiceLocator services;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<UiState> state = new MutableLiveData<>(new UiState(true, List.of(), false));
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();

    private File repoDirectory;
    private String path = "";
    private RepoFiles files;
    private List<Row> allRows = List.of();
    private FileStatusIndex index = FileStatusIndex.EMPTY;
    private String query = "";
    private boolean loading = true;
    private int loadGeneration;

    public FilesViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
    }

    /** Legt Repo und Ordner fest; ein zweiter Aufruf ändert nichts. */
    public void init(
            @NonNull final String directory,
            @NonNull final String relativePath
    ) {
        if (repoDirectory != null) {
            return;
        }
        repoDirectory = new File(directory);
        path = relativePath;
        files = new RepoFiles(repoDirectory);
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
    public File repoDirectory() {
        return repoDirectory;
    }

    @NonNull
    public String path() {
        return path;
    }

    @NonNull
    public File fileOf(
            @NonNull final RepoFiles.Entry entry
    ) throws RepoFiles.FilesException {
        return files.resolve(entry.path());
    }

    public void setQuery(
            @NonNull final String newQuery
    ) {
        query = newQuery.trim();
        publish();
    }

    /** Liest Ordner und Git-Markierungen neu. */
    public void reload() {
        loading = true;
        publish();
        loadGeneration += 1;
        final int generation = loadGeneration;
        services.io().execute(() -> {
            List<RepoFiles.Entry> entries = List.of();
            String error = null;
            try {
                entries = files.list(path);
            } catch (final RepoFiles.FilesException failed) {
                error = describe(failed);
            }
            final List<RepoFiles.Entry> listed = entries;
            final String failure = error;
            main.post(() -> {
                if (generation != loadGeneration) {
                    return;
                }
                allRows = rowsFor(listed, index);
                loading = false;
                publish();
                if (failure != null) {
                    messages.setValue(new Event<>(failure));
                }
            });
            loadStatus(listed, generation);
        });
    }

    /** Markierungen kommen nach, weil die Statusprüfung auf dem Telefonspeicher langsam sein kann. */
    private void loadStatus(
            final List<RepoFiles.Entry> listed,
            final int generation
    ) {
        FileStatusIndex loaded = FileStatusIndex.EMPTY;
        try {
            loaded = FileStatusIndex.of(services.engine().status(repoDirectory));
        } catch (final GitFailureException notARepo) {
            // Ohne Status gibt es eben keine Markierungen.
        }
        final FileStatusIndex result = loaded;
        main.post(() -> {
            if (generation != loadGeneration) {
                return;
            }
            index = result;
            allRows = rowsFor(listed, result);
            publish();
        });
    }

    public void createFile(
            @NonNull final String name
    ) {
        run(() -> files.createFile(path, name));
    }

    public void createFolder(
            @NonNull final String name
    ) {
        run(() -> files.createFolder(path, name));
    }

    public void rename(
            @NonNull final RepoFiles.Entry entry,
            @NonNull final String newName
    ) {
        run(() -> files.rename(entry.path(), newName));
    }

    public void delete(
            @NonNull final RepoFiles.Entry entry
    ) {
        run(() -> {
            files.delete(entry.path());
            return entry.path();
        });
    }

    /** Eine Dateioperation, die scheitern darf und einen Pfad liefert. */
    private interface FileTask {

        String run() throws RepoFiles.FilesException;
    }

    private void run(
            @NonNull final FileTask task
    ) {
        services.io().execute(() -> {
            String error = null;
            try {
                task.run();
            } catch (final RepoFiles.FilesException failed) {
                error = describe(failed);
            }
            final String failure = error;
            main.post(() -> {
                if (failure != null) {
                    messages.setValue(new Event<>(failure));
                }
                reload();
            });
        });
    }

    private String describe(
            final RepoFiles.FilesException failed
    ) {
        switch (failed.reason()) {
            case INVALID_NAME:
                return getApplication().getString(R.string.files_error_invalid_name);
            case EXISTS:
                return getApplication().getString(R.string.files_error_exists);
            case NOT_FOUND:
                return getApplication().getString(R.string.files_error_not_found);
            case PROTECTED:
                return getApplication().getString(R.string.files_error_protected);
            case IO:
            default:
                return getApplication().getString(R.string.files_error_io);
        }
    }

    private static List<Row> rowsFor(
            final List<RepoFiles.Entry> entries,
            final FileStatusIndex index
    ) {
        final List<Row> rows = new ArrayList<>(entries.size());
        for (final RepoFiles.Entry entry : entries) {
            if (entry.directory()) {
                rows.add(new Row(entry, null, index.hasChangesIn(entry.path())));
            } else {
                rows.add(new Row(entry, index.kindOf(entry.path()).orElse(null), false));
            }
        }
        return rows;
    }

    private void publish() {
        final String needle = query.toLowerCase(Locale.ROOT);
        final List<Row> visible = new ArrayList<>();
        for (final Row row : allRows) {
            if (needle.isEmpty() || row.entry().name().toLowerCase(Locale.ROOT).contains(needle)) {
                visible.add(row);
            }
        }
        state.setValue(new UiState(loading, visible, allRows.isEmpty() && !loading));
    }
}
