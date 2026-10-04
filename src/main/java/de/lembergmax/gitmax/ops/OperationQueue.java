package de.lembergmax.gitmax.ops;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.git.GitEngine;
import de.lembergmax.gitmax.git.GitErrorMapper;
import de.lembergmax.gitmax.git.GitProgress;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * Wartet Git-Vorgänge ab und führt sie auf Arbeits-Threads aus. Es läuft höchstens ein Klon zugleich,
 * andere Vorgänge höchstens zu zweit, und je Repo-Ordner nie mehr als ein Vorgang. Konflikte und Fehler
 * eines Vorgangs halten die übrigen nicht auf.
 *
 * <p>Frei von Android-Klassen; Änderungen kommen über {@link Listener} auf dem Arbeits-Thread an.</p>
 */
public final class OperationQueue {

    /** Wird nach jeder Änderung mit dem vollständigen Stand aufgerufen, älteste Vorgänge zuerst. */
    public interface Listener {

        void onChanged(
                @NonNull List<OperationEntry> entries
        );
    }

    /** Höchstens so viele Klone laufen zugleich. */
    public static final int MAX_CLONES = 1;

    /** Höchstens so viele andere Vorgänge laufen zugleich. */
    public static final int MAX_OTHERS = 2;

    /** Mindestabstand zwischen zwei Fortschrittsmeldungen desselben Abschnitts. */
    private static final long PROGRESS_INTERVAL_MS = 120L;

    /**
     * So lange gilt „Trotzdem versuchen“ bei „Nur im WLAN“ für alle weiteren Vorgänge: Wer einen Stapel Repos
     * bewusst über mobile Daten holt, soll nicht jeden einzeln bestätigen.
     */
    static final long METERED_WINDOW_MS = 10L * 60L * 1000L;

    private final GitEngine engine;
    private final Executor workers;
    private final LongSupplier clock;
    private final OperationHistory history;
    private final NetworkPolicy network;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<Slot> slots = new ArrayList<>();
    private final Object lock = new Object();
    private long nextId;
    private long meteredAllowedUntil;

    /**
     * @param engine  führt die Git-Operationen aus
     * @param workers Arbeits-Threads; mindestens {@link #MAX_CLONES} + {@link #MAX_OTHERS}, sonst warten
     *                Vorgänge im Executor statt in der Warteschlange
     * @param clock   Zeit in Millisekunden
     * @param history Speicher für den Verlauf
     */
    public OperationQueue(
            @NonNull final GitEngine engine,
            @NonNull final Executor workers,
            @NonNull final LongSupplier clock,
            @NonNull final OperationHistory history
    ) {
        this(engine, workers, clock, history, NetworkPolicy.ALWAYS);
    }

    /**
     * @param network entscheidet, ob ein neuer Vorgang das Netz nutzen darf; sonst scheitert er sofort mit
     *                {@link GitFailureKind#METERED_NETWORK}
     */
    public OperationQueue(
            @NonNull final GitEngine engine,
            @NonNull final Executor workers,
            @NonNull final LongSupplier clock,
            @NonNull final OperationHistory history,
            @NonNull final NetworkPolicy network
    ) {
        this.network = Objects.requireNonNull(network, "network");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.history = Objects.requireNonNull(history, "history");
        long highest = 0L;
        for (final OperationEntry entry : history.load(clock.getAsLong())) {
            slots.add(new Slot(entry, null));
            highest = Math.max(highest, entry.id());
        }
        this.nextId = highest + 1L;
    }

    public void addListener(
            @NonNull final Listener listener
    ) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(
            @NonNull final Listener listener
    ) {
        listeners.remove(listener);
    }

    /** Reiht einen Vorgang ein und startet ihn, sobald die Grenzen es erlauben. */
    public long enqueue(
            @NonNull final Operation operation
    ) {
        Objects.requireNonNull(operation, "operation");
        final long id;
        synchronized (lock) {
            id = nextId;
            nextId += 1L;
            final OperationEntry entry = new OperationEntry(
                    id, operation.kind(), operation.title(), operation.directory().getAbsolutePath(),
                    OperationState.QUEUED, null, null, null, clock.getAsLong(), 0L, 0L);
            final Slot slot = new Slot(entry, operation);
            slots.add(slot);
            if (networkAllows()) {
                startPossible();
            } else {
                slot.entry = entry.ended(OperationState.FAILED,
                        new OperationEntry.Failure(GitFailureKind.METERED_NETWORK, "Allowed on Wi-Fi only", List.of()),
                        clock.getAsLong());
            }
        }
        changed();
        return id;
    }

    /** Stellt einen gescheiterten oder abgebrochenen Vorgang erneut ein. */
    @NonNull
    public OptionalLong retry(
            final long id
    ) {
        final Operation operation;
        synchronized (lock) {
            final Slot slot = find(id);
            if (slot == null || slot.operation == null
                    || (slot.entry.state() != OperationState.FAILED && slot.entry.state() != OperationState.CANCELLED)) {
                return OptionalLong.empty();
            }
            operation = slot.operation;
            final OperationEntry.Failure reason = slot.entry.failure();
            if (reason != null && reason.kind() == GitFailureKind.METERED_NETWORK) {
                // Wer einen so gescheiterten Vorgang wiederholt, will ihn über mobile Daten.
                meteredAllowedUntil = clock.getAsLong() + METERED_WINDOW_MS;
            }
        }
        return OptionalLong.of(enqueue(operation));
    }

    /** {@code true}, wenn {@link #retry(long)} den Vorgang erneut einstellen kann. */
    public boolean canRetry(
            final long id
    ) {
        synchronized (lock) {
            final Slot slot = find(id);
            return slot != null && slot.operation != null
                    && (slot.entry.state() == OperationState.FAILED || slot.entry.state() == OperationState.CANCELLED);
        }
    }

    /** Bricht einen wartenden Vorgang ab, einen laufenden bei der nächsten Gelegenheit der Engine. */
    public void cancel(
            final long id
    ) {
        synchronized (lock) {
            final Slot slot = find(id);
            if (slot == null) {
                return;
            }
            cancelSlot(slot);
        }
        changed();
    }

    /** Bricht alle wartenden und laufenden Vorgänge ab. */
    public void cancelAll() {
        synchronized (lock) {
            for (final Slot slot : slots) {
                cancelSlot(slot);
            }
        }
        changed();
    }

    /** Entfernt alle beendeten Vorgänge aus der Liste. */
    public void clearFinished() {
        synchronized (lock) {
            final Iterator<Slot> iterator = slots.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().entry.state().isFinished()) {
                    iterator.remove();
                }
            }
        }
        changed();
    }

    /** Der aktuelle Stand, älteste Vorgänge zuerst. */
    @NonNull
    public List<OperationEntry> snapshot() {
        synchronized (lock) {
            return entries();
        }
    }

    /** {@code true}, solange Vorgänge warten oder laufen. */
    public boolean hasActive() {
        synchronized (lock) {
            for (final Slot slot : slots) {
                if (!slot.entry.state().isFinished()) {
                    return true;
                }
            }
            return false;
        }
    }

    /* ------------------------------------------------------------------------------------------ */

    private void cancelSlot(
            final Slot slot
    ) {
        if (slot.entry.state() == OperationState.QUEUED) {
            slot.entry = slot.entry.ended(OperationState.CANCELLED,
                    new OperationEntry.Failure(GitFailureKind.CANCELLED, "Cancelled", List.of()), clock.getAsLong());
        } else if (slot.entry.state() == OperationState.RUNNING) {
            slot.cancelled = true;
        }
    }

    /** Hält {@code lock}. */
    private boolean networkAllows() {
        return clock.getAsLong() < meteredAllowedUntil || network.allowsNow();
    }

    /** Startet alle wartenden Vorgänge, die unter den Grenzen Platz haben. Hält {@code lock}. */
    private void startPossible() {
        for (final Slot slot : slots) {
            if (slot.entry.state() == OperationState.QUEUED && mayStart(slot)) {
                slot.entry = slot.entry.running(clock.getAsLong());
                workers.execute(() -> run(slot));
            }
        }
    }

    private boolean mayStart(
            final Slot candidate
    ) {
        int clones = 0;
        int others = 0;
        for (final Slot slot : slots) {
            if (slot.entry.state() != OperationState.RUNNING) {
                continue;
            }
            if (slot.entry.directory().equals(candidate.entry.directory())) {
                return false;
            }
            if (slot.entry.kind() == OperationKind.CLONE) {
                clones += 1;
            } else {
                others += 1;
            }
        }
        return candidate.entry.kind() == OperationKind.CLONE ? clones < MAX_CLONES : others < MAX_OTHERS;
    }

    private void run(
            final Slot slot
    ) {
        changed();
        final Operation operation = Objects.requireNonNull(slot.operation, "operation");
        OperationOutcome outcome = null;
        OperationEntry.Failure failure = null;
        try {
            outcome = operation.execute(engine, new SlotProgress(slot));
        } catch (final GitFailureException failed) {
            failure = new OperationEntry.Failure(failed.kind(), failed.getMessage(), failed.paths());
        } catch (final RuntimeException | Error unexpected) {
            // Auch ein Error (zu wenig Speicher bei einem großen Klon, ein fehlendes JGit-Detail auf dem Gerät) beendet den
            // Vorgang als gescheitert: sonst bliebe er ewig "laufend", sperrte seinen Ordner und hielte den Dienst am Leben.
            final GitFailureException mapped = GitErrorMapper.map(unexpected, slot.cancelled);
            failure = new OperationEntry.Failure(mapped.kind(), mapped.getMessage(), mapped.paths());
        }
        finish(slot, outcome, failure);
    }

    private void finish(
            final Slot slot,
            @Nullable final OperationOutcome outcome,
            @Nullable final OperationEntry.Failure failure
    ) {
        synchronized (lock) {
            final long now = clock.getAsLong();
            if (outcome != null) {
                slot.entry = slot.entry.succeeded(outcome, now);
            } else {
                final OperationEntry.Failure reason = Objects.requireNonNull(failure, "failure");
                final OperationState state = reason.kind() == GitFailureKind.CANCELLED
                        ? OperationState.CANCELLED
                        : OperationState.FAILED;
                slot.entry = slot.entry.ended(state, reason, now);
            }
            startPossible();
        }
        changed();
    }

    /** Speichert den Stand und meldet ihn allen Zuhörern; läuft ohne {@code lock}. */
    private void changed() {
        final List<OperationEntry> current;
        synchronized (lock) {
            current = entries();
            history.save(current);
        }
        notifyListeners(current);
    }

    private void notifyListeners(
            final List<OperationEntry> current
    ) {
        for (final Listener listener : listeners) {
            listener.onChanged(current);
        }
    }

    private List<OperationEntry> entries() {
        final List<OperationEntry> entries = new ArrayList<>(slots.size());
        for (final Slot slot : slots) {
            entries.add(slot.entry);
        }
        return List.copyOf(entries);
    }

    @Nullable
    private Slot find(
            final long id
    ) {
        for (final Slot slot : slots) {
            if (slot.entry.id() == id) {
                return slot;
            }
        }
        return null;
    }

    /** Veränderlicher Platz eines Vorgangs; {@code entry} und {@code cancelled} ändern sich unter {@code lock}. */
    private static final class Slot {

        private volatile OperationEntry entry;
        private volatile boolean cancelled;
        @Nullable
        private final Operation operation;

        private Slot(
                final OperationEntry entry,
                @Nullable final Operation operation
        ) {
            this.entry = entry;
            this.operation = operation;
        }
    }

    /** Fortschritt eines laufenden Vorgangs: merkt sich den Stand und meldet gedrosselt weiter. */
    private final class SlotProgress implements GitProgress {

        private final Slot slot;
        private Phase lastPhase;
        private long lastReport;

        private SlotProgress(
                final Slot slot
        ) {
            this.slot = slot;
        }

        @Override
        public void onProgress(
                @NonNull final Phase phase,
                final float fraction
        ) {
            final long now = clock.getAsLong();
            final boolean phaseChanged = phase != lastPhase;
            final boolean done = fraction >= 1f;
            if (!phaseChanged && !done && now - lastReport < PROGRESS_INTERVAL_MS) {
                return;
            }
            lastPhase = phase;
            lastReport = now;
            final List<OperationEntry> current;
            synchronized (lock) {
                if (slot.entry.state() != OperationState.RUNNING) {
                    return;
                }
                slot.entry = slot.entry.withProgress(new OperationEntry.Progress(phase, fraction));
                current = entries();
            }
            notifyListeners(current);
        }

        @Override
        public boolean isCancelled() {
            return slot.cancelled;
        }
    }
}
