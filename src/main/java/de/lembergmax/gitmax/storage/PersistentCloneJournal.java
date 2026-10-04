package de.lembergmax.gitmax.storage;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.CloneJournal;

import org.eclipse.jgit.util.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * {@link CloneJournal} im {@link KeyValueStore}. Die Liste der laufenden Klone überlebt einen
 * Prozess-Tod; {@link #cleanUpInterrupted()} löscht beim Start, was davon übrig ist.
 */
public final class PersistentCloneJournal implements CloneJournal {

    private static final String KEY_PENDING = "clone.pending.v1";

    private final KeyValueStore store;

    public PersistentCloneJournal(
            @NonNull final KeyValueStore store
    ) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public synchronized void begin(
            @NonNull final File target
    ) {
        final Set<String> pending = pending();
        pending.add(target.getAbsolutePath());
        save(pending);
    }

    @Override
    public synchronized void end(
            @NonNull final File target
    ) {
        final Set<String> pending = pending();
        if (pending.remove(target.getAbsolutePath())) {
            save(pending);
        }
    }

    /**
     * Löscht alle Ordner, deren Klon nie zu Ende ging, und leert das Journal.
     *
     * @return die gelöschten Ordner
     */
    @NonNull
    public synchronized List<File> cleanUpInterrupted() {
        final List<File> removed = new ArrayList<>();
        for (final String path : pending()) {
            final File folder = new File(path);
            try {
                FileUtils.delete(folder, FileUtils.RECURSIVE | FileUtils.SKIP_MISSING | FileUtils.IGNORE_ERRORS);
                removed.add(folder);
            } catch (final IOException cannotDelete) {
                // Bleibt liegen; der Nutzer kann den Ordner von Hand löschen.
            }
        }
        store.remove(KEY_PENDING);
        return removed;
    }

    private Set<String> pending() {
        final Set<String> pending = new LinkedHashSet<>();
        final String raw = store.get(KEY_PENDING).orElse("");
        if (raw.isBlank()) {
            return pending;
        }
        try {
            final JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index += 1) {
                final String path = array.optString(index, "");
                if (!path.isBlank()) {
                    pending.add(path);
                }
            }
        } catch (final JSONException malformed) {
            return new LinkedHashSet<>();
        }
        return pending;
    }

    private void save(
            final Set<String> pending
    ) {
        if (pending.isEmpty()) {
            store.remove(KEY_PENDING);
        } else {
            store.put(KEY_PENDING, new JSONArray(pending).toString());
        }
    }
}
