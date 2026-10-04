package de.lembergmax.gitmax.ui.picker;

import android.app.Application;
import android.content.Context;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.FolderName;
import de.lembergmax.gitmax.storage.StoragePermission;
import de.lembergmax.gitmax.ui.common.Event;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Zustand der Ordnerauswahl: der aktuelle Ordner (oder die Liste der Speicher), seine Unterordner
 * und das Anlegen neuer Ordner.
 */
public final class FolderPickerViewModel extends AndroidViewModel {

    /** Art eines Eintrags in der Liste. */
    public enum Type {
        VOLUME,
        UP,
        FOLDER
    }

    /**
     * Ein Eintrag der Liste.
     *
     * @param type   Art
     * @param label  angezeigter Name
     * @param file   zugehöriger Ordner ({@code null} bei {@link Type#UP}: der Elternordner steckt im Listing)
     * @param isRepo {@code true}, wenn der Ordner ein Git-Repo ist
     */
    public record Entry(
            @NonNull Type type,
            @NonNull String label,
            @Nullable File file,
            boolean isRepo
    ) {
    }

    /**
     * Inhalt der Ansicht.
     *
     * @param directory aktueller Ordner, {@code null} in der Liste der Speicher
     * @param parent    Elternordner, {@code null} an der Wurzel eines Speichers oder in der Speicherliste
     * @param entries   die Einträge
     */
    public record Listing(
            @Nullable File directory,
            @Nullable File parent,
            @NonNull List<Entry> entries
    ) {
    }

    private final ServiceLocator services;
    private final MutableLiveData<Listing> listing = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private List<File> volumeRoots = List.of();

    public FolderPickerViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
    }

    @NonNull
    public LiveData<Listing> listing() {
        return listing;
    }

    /** Einmalige Fehlermeldungen (nicht lesbar, Name ungültig, …). */
    @NonNull
    public LiveData<Event<String>> messages() {
        return messages;
    }

    /** Öffnet zu Beginn den gewünschten Ordner oder den Telefonspeicher. */
    public void start(
            @Nullable final String startPath
    ) {
        if (listing.getValue() != null) {
            return;
        }
        final File start = startPath == null ? StoragePermission.primaryStorageRoot() : new File(startPath);
        open(start.isDirectory() ? start : StoragePermission.primaryStorageRoot());
    }

    /** Wechselt in einen Ordner. */
    public void open(
            @NonNull final File directory
    ) {
        services.io().execute(() -> {
            final File[] children = directory.listFiles(File::isDirectory);
            if (children == null) {
                messages.postValue(new Event<>(text(R.string.picker_error_unreadable)));
                return;
            }
            final List<Entry> entries = new ArrayList<>();
            final File parent = parentOf(directory);
            if (parent != null || volumeRoots().size() > 1) {
                entries.add(new Entry(Type.UP, text(R.string.picker_up), null, false));
            }
            final List<File> sorted = new ArrayList<>(List.of(children));
            sorted.sort(Comparator.comparing(file -> file.getName().toLowerCase(Locale.ROOT)));
            for (final File child : sorted) {
                if (!child.getName().startsWith(".")) {
                    entries.add(new Entry(Type.FOLDER, child.getName(), child, new File(child, ".git").exists()));
                }
            }
            listing.postValue(new Listing(directory, parent, entries));
        });
    }

    /** Eine Ebene höher; an der Wurzel eines Speichers zur Liste der Speicher. */
    public void up() {
        final Listing current = listing.getValue();
        if (current == null || current.directory() == null) {
            return;
        }
        if (current.parent() != null) {
            open(current.parent());
        } else {
            showVolumes();
        }
    }

    /** Zeigt die Liste der Speicher. */
    public void showVolumes() {
        final List<Entry> entries = new ArrayList<>();
        final StorageManager manager = (StorageManager) getApplication().getSystemService(Context.STORAGE_SERVICE);
        for (final StorageVolume volume : manager.getStorageVolumes()) {
            final File directory = volume.getDirectory();
            if (directory != null) {
                entries.add(new Entry(Type.VOLUME, volume.getDescription(getApplication()), directory, false));
            }
        }
        listing.setValue(new Listing(null, null, entries));
    }

    /** Legt unter dem aktuellen Ordner einen Ordner an und wechselt hinein. */
    public void createFolder(
            @NonNull final String rawName
    ) {
        final Listing current = listing.getValue();
        if (current == null || current.directory() == null) {
            return;
        }
        final String name = rawName.strip();
        if (!FolderName.isValid(name)) {
            messages.setValue(new Event<>(text(R.string.picker_error_name)));
            return;
        }
        final File target = new File(current.directory(), name);
        services.io().execute(() -> {
            if (target.exists()) {
                messages.postValue(new Event<>(text(R.string.picker_error_exists)));
            } else if (!target.mkdirs()) {
                messages.postValue(new Event<>(text(R.string.picker_error_create)));
            } else {
                open(target);
            }
        });
    }

    /** Elternordner, aber nie oberhalb der Wurzel eines Speichers. */
    @Nullable
    private File parentOf(
            @NonNull final File directory
    ) {
        for (final File volumeRoot : volumeRoots()) {
            if (volumeRoot.equals(directory)) {
                return null;
            }
        }
        return directory.getParentFile();
    }

    private List<File> volumeRoots() {
        if (volumeRoots.isEmpty()) {
            final StorageManager manager = (StorageManager) getApplication().getSystemService(Context.STORAGE_SERVICE);
            final List<File> roots = new ArrayList<>();
            for (final StorageVolume volume : manager.getStorageVolumes()) {
                if (volume.getDirectory() != null) {
                    roots.add(volume.getDirectory());
                }
            }
            volumeRoots = roots;
        }
        return volumeRoots;
    }

    private String text(
            final int resource
    ) {
        return getApplication().getString(resource);
    }
}
