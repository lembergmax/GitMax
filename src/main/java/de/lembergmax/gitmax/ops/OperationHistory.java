package de.lembergmax.gitmax.ops;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.storage.KeyValueStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bewahrt die Vorgänge über App-Starts auf: die letzten {@value #MAX_FINISHED} beendeten und alle
 * noch offenen. Offene Vorgänge, die ein Prozess-Tod unterbrochen hat, werden beim Laden zu
 * „abgebrochen“.
 */
public final class OperationHistory {

    /** So viele beendete Vorgänge bleiben erhalten. */
    public static final int MAX_FINISHED = 50;

    /** Meldung für Vorgänge, die der Prozess-Tod unterbrochen hat. */
    static final String INTERRUPTED_MESSAGE = "App terminated";

    private static final String KEY = "ops.history.v1";

    private final KeyValueStore store;

    public OperationHistory(
            @NonNull final KeyValueStore store
    ) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Liest die gespeicherten Vorgänge, älteste zuerst. Offene gelten als abgebrochen. */
    @NonNull
    public List<OperationEntry> load(
            final long now
    ) {
        final List<OperationEntry> entries = new ArrayList<>();
        final String raw = store.get(KEY).orElse("");
        if (raw.isBlank()) {
            return entries;
        }
        try {
            final JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index += 1) {
                final OperationEntry entry = decode(array.getJSONObject(index), now);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (final JSONException malformed) {
            return new ArrayList<>();
        }
        return entries;
    }

    /** Speichert die Vorgänge, älteste zuerst; ältere beendete über der Grenze fallen weg. */
    public void save(
            @NonNull final List<OperationEntry> entries
    ) {
        final List<OperationEntry> kept = new ArrayList<>(entries);
        int finished = (int) kept.stream().filter(entry -> entry.state().isFinished()).count();
        int index = 0;
        while (index < kept.size() && finished > MAX_FINISHED) {
            if (kept.get(index).state().isFinished()) {
                kept.remove(index);
                finished -= 1;
            } else {
                index += 1;
            }
        }
        final JSONArray array = new JSONArray();
        for (final OperationEntry entry : kept) {
            array.put(encode(entry));
        }
        store.put(KEY, array.toString());
    }

    private static JSONObject encode(
            final OperationEntry entry
    ) {
        try {
            final JSONObject json = new JSONObject();
            json.put("id", entry.id());
            json.put("kind", entry.kind().name());
            json.put("title", entry.title());
            json.put("directory", entry.directory());
            json.put("state", entry.state().name());
            json.put("enqueuedAt", entry.enqueuedAt());
            json.put("startedAt", entry.startedAt());
            json.put("finishedAt", entry.finishedAt());
            if (entry.outcome() != null) {
                json.put("outcome", entry.outcome().name());
            }
            if (entry.failure() != null) {
                final JSONObject failure = new JSONObject();
                failure.put("kind", entry.failure().kind().name());
                failure.put("message", entry.failure().message());
                failure.put("paths", new JSONArray(entry.failure().paths()));
                json.put("failure", failure);
            }
            return json;
        } catch (final JSONException impossible) {
            throw new IllegalStateException("Operation cannot be serialized", impossible);
        }
    }

    private static OperationEntry decode(
            final JSONObject json,
            final long now
    ) {
        try {
            final OperationKind kind = OperationKind.valueOf(json.getString("kind"));
            final OperationState state = OperationState.valueOf(json.getString("state"));
            final OperationOutcome outcome = json.has("outcome")
                    ? OperationOutcome.valueOf(json.getString("outcome"))
                    : null;
            final OperationEntry.Failure failure = json.has("failure")
                    ? decodeFailure(json.getJSONObject("failure"))
                    : null;
            final OperationEntry entry = new OperationEntry(
                    json.getLong("id"), kind, json.getString("title"), json.getString("directory"), state,
                    null, outcome, failure,
                    json.getLong("enqueuedAt"), json.getLong("startedAt"), json.getLong("finishedAt"));
            if (state.isFinished()) {
                return entry;
            }
            return entry.ended(OperationState.CANCELLED,
                    new OperationEntry.Failure(GitFailureKind.CANCELLED, INTERRUPTED_MESSAGE, List.of()), now);
        } catch (final JSONException | IllegalArgumentException unreadable) {
            return null;
        }
    }

    private static OperationEntry.Failure decodeFailure(
            final JSONObject json
    ) throws JSONException {
        final List<String> paths = new ArrayList<>();
        final JSONArray array = json.optJSONArray("paths");
        for (int index = 0; array != null && index < array.length(); index += 1) {
            paths.add(array.getString(index));
        }
        return new OperationEntry.Failure(
                GitFailureKind.valueOf(json.getString("kind")), json.getString("message"), paths);
    }
}
