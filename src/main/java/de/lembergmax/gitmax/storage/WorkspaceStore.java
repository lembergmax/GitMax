package de.lembergmax.gitmax.storage;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Merkt sich die Arbeitsordner (dort sucht GitMax Repos) und den Standard-Zielordner für neue Klone.
 * Der Standard-Zielordner ist immer einer der Arbeitsordner.
 */
public final class WorkspaceStore {

    private static final String KEY_ROOTS = "workspace.roots.v1";
    private static final String KEY_DEFAULT = "workspace.default.v1";

    private final KeyValueStore store;

    public WorkspaceStore(
            @NonNull final KeyValueStore store
    ) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Alle Arbeitsordner in der Reihenfolge des Hinzufügens. */
    @NonNull
    public synchronized List<File> roots() {
        final List<File> roots = new ArrayList<>();
        final String raw = store.get(KEY_ROOTS).orElse("");
        if (raw.isBlank()) {
            return roots;
        }
        try {
            final JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index += 1) {
                final String path = array.optString(index, "");
                if (!path.isBlank()) {
                    roots.add(new File(path));
                }
            }
        } catch (final JSONException malformed) {
            return new ArrayList<>();
        }
        return roots;
    }

    /** Fügt einen Arbeitsordner hinzu; der erste wird zugleich Standard-Zielordner. Doppelte werden ignoriert. */
    public synchronized void addRoot(
            @NonNull final File root
    ) {
        Objects.requireNonNull(root, "root");
        final File absolute = root.getAbsoluteFile();
        final List<File> roots = roots();
        if (roots.contains(absolute)) {
            return;
        }
        roots.add(absolute);
        save(roots);
        if (defaultTarget().isEmpty()) {
            setDefaultTarget(absolute);
        }
    }

    /** Entfernt einen Arbeitsordner (nicht von der Platte). War er der Standard, rückt der erste verbleibende nach. */
    public synchronized void removeRoot(
            @NonNull final File root
    ) {
        Objects.requireNonNull(root, "root");
        final List<File> roots = roots();
        if (!roots.remove(root.getAbsoluteFile())) {
            return;
        }
        save(roots);
        final Optional<File> current = defaultTarget();
        if (current.isEmpty() || current.get().equals(root.getAbsoluteFile())) {
            if (roots.isEmpty()) {
                store.remove(KEY_DEFAULT);
            } else {
                store.put(KEY_DEFAULT, roots.get(0).getPath());
            }
        }
    }

    /** Standard-Zielordner für neue Klone, leer wenn noch kein Arbeitsordner gewählt ist. */
    @NonNull
    public synchronized Optional<File> defaultTarget() {
        return store.get(KEY_DEFAULT)
                .filter(path -> !path.isBlank())
                .map(File::new)
                .filter(target -> roots().contains(target));
    }

    /** Setzt den Standard-Zielordner; er muss bereits ein Arbeitsordner sein. */
    public synchronized void setDefaultTarget(
            @NonNull final File target
    ) {
        Objects.requireNonNull(target, "target");
        final File absolute = target.getAbsoluteFile();
        if (!roots().contains(absolute)) {
            throw new IllegalArgumentException("Not a workspace folder: " + absolute);
        }
        store.put(KEY_DEFAULT, absolute.getPath());
    }

    private void save(
            final List<File> roots
    ) {
        final JSONArray array = new JSONArray();
        for (final File root : roots) {
            array.put(root.getPath());
        }
        store.put(KEY_ROOTS, array.toString());
    }
}
