package de.lembergmax.gitmax.ui.viewer;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.storage.RepoFiles;
import de.lembergmax.gitmax.storage.TextFileIo;
import de.lembergmax.gitmax.ui.common.Event;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Lädt eine Datei zum Ansehen oder Bearbeiten und speichert sie zurück. Zustand nur auf dem Hauptthread. */
public final class FileViewerViewModel extends AndroidViewModel {

    /** Was der Bildschirm zeigt. */
    public enum Content {
        LOADING,
        TEXT,
        IMAGE,
        BINARY,
        NOT_UTF8,
        TOO_LARGE,
        ERROR
    }

    /** Ausgang des Speicherns. */
    public enum SaveOutcome {
        SAVED,
        /** Die Datei hat sich seit dem Öffnen geändert; Überschreiben braucht eine Bestätigung. */
        CHANGED_ON_DISK,
        FAILED
    }

    /**
     * Zustand des Bildschirms.
     *
     * @param content Art des Inhalts
     * @param file    die Datei
     * @param text    gelesener Text, nur bei {@link Content#TEXT}
     * @param image   Vorschau, nur bei {@link Content#IMAGE}
     * @param size    Größe der Datei in Byte
     */
    public record UiState(
            @NonNull Content content,
            @NonNull File file,
            @Nullable TextFileIo.Result text,
            @Nullable Bitmap image,
            long size
    ) {
    }

    /** Größte Datei, die nach der Rückfrage „Trotzdem öffnen“ noch gelesen wird. */
    private static final long OPEN_ANYWAY_LIMIT_BYTES = 10L * 1024 * 1024;
    private static final int MAX_IMAGE_EDGE_PX = 2048;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp");

    private final ServiceLocator services;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<SaveOutcome>> saveOutcomes = new MutableLiveData<>();

    private File file;
    private String path = "";
    private TextFileIo.Result text;
    private String draft;

    public FileViewerViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
    }

    /** Legt die Datei fest und lädt sie; ein zweiter Aufruf ändert nichts. */
    public void init(
            @NonNull final String directory,
            @NonNull final String relativePath
    ) {
        if (file != null) {
            return;
        }
        path = relativePath;
        try {
            file = new RepoFiles(new File(directory)).resolve(relativePath);
        } catch (final RepoFiles.FilesException notAllowed) {
            file = new File(directory, ".gitmax-ungueltig");
            state.setValue(new UiState(Content.ERROR, file, null, null, 0L));
            return;
        }
        state.setValue(new UiState(Content.LOADING, file, null, null, 0L));
        load(TextFileIo.VIEW_LIMIT_BYTES);
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }

    @NonNull
    public LiveData<Event<SaveOutcome>> saveOutcomes() {
        return saveOutcomes;
    }

    @NonNull
    public File file() {
        return file;
    }

    @NonNull
    public String path() {
        return path;
    }

    /**
     * Merkt den ungespeicherten Text des Editors, damit er eine Drehung des Bildschirms übersteht; {@code null}
     * verwirft ihn. Nach dem Ende des Prozesses ist er weg.
     */
    public void keepDraft(
            @Nullable final String unsavedText
    ) {
        draft = unsavedText;
    }

    /** Der gemerkte, noch nicht gespeicherte Text. */
    @NonNull
    public Optional<String> draft() {
        return Optional.ofNullable(draft);
    }

    /** Öffnet eine große Datei nach der Rückfrage doch. */
    public void openAnyway() {
        state.setValue(new UiState(Content.LOADING, file, null, null, file.length()));
        load(OPEN_ANYWAY_LIMIT_BYTES);
    }

    /**
     * Schreibt den Text zurück.
     *
     * @param force speichert auch, wenn die Datei seit dem Öffnen verändert wurde
     */
    public void save(
            @NonNull final String newText,
            final boolean force
    ) {
        final TextFileIo.Result original = text;
        if (original == null) {
            return;
        }
        services.io().execute(() -> {
            SaveOutcome outcome;
            TextFileIo.Result updated = original;
            try {
                if (!force && TextFileIo.changedSince(file, original)) {
                    outcome = SaveOutcome.CHANGED_ON_DISK;
                } else {
                    TextFileIo.write(file, newText, original.lineEnding(), original.bom());
                    updated = new TextFileIo.Result(original.kind(), newText, original.lineEnding(), original.bom(),
                            file.length(), file.lastModified());
                    outcome = SaveOutcome.SAVED;
                }
            } catch (final IOException failed) {
                outcome = SaveOutcome.FAILED;
            }
            final SaveOutcome result = outcome;
            final TextFileIo.Result baseline = updated;
            main.post(() -> {
                text = baseline;
                saveOutcomes.setValue(new Event<>(result));
            });
        });
    }

    private void load(
            final long limit
    ) {
        services.io().execute(() -> {
            UiState loaded;
            try {
                loaded = isImage(file) ? imageState() : textState(limit);
            } catch (final IOException unreadable) {
                loaded = new UiState(Content.ERROR, file, null, null, file.length());
            }
            final UiState result = loaded;
            main.post(() -> {
                text = result.text();
                state.setValue(result);
            });
        });
    }

    private UiState textState(
            final long limit
    ) throws IOException {
        final TextFileIo.Result read = TextFileIo.read(file, limit);
        switch (read.kind()) {
            case TEXT:
                return new UiState(Content.TEXT, file, read, null, read.size());
            case BINARY:
                return new UiState(Content.BINARY, file, null, null, read.size());
            case NOT_UTF8:
                return new UiState(Content.NOT_UTF8, file, null, null, read.size());
            case TOO_LARGE:
            default:
                return new UiState(Content.TOO_LARGE, file, null, null, read.size());
        }
    }

    private UiState imageState() {
        final BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        final BitmapFactory.Options options = new BitmapFactory.Options();
        int sample = 1;
        while (bounds.outWidth / sample > MAX_IMAGE_EDGE_PX || bounds.outHeight / sample > MAX_IMAGE_EDGE_PX) {
            sample *= 2;
        }
        options.inSampleSize = sample;
        final Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        return bitmap == null
                ? new UiState(Content.ERROR, file, null, null, file.length())
                : new UiState(Content.IMAGE, file, null, bitmap, file.length());
    }

    private static boolean isImage(
            final File file
    ) {
        final String name = file.getName();
        final int dot = name.lastIndexOf('.');
        return dot >= 0 && IMAGE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
