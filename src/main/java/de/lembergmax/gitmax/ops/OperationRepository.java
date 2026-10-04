package de.lembergmax.gitmax.ops;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Die Schnittstelle der Oberfläche zu den Git-Vorgängen: reiht Vorgänge ein, startet den
 * Vordergrunddienst und stellt den Stand als {@link LiveData} bereit.
 */
public final class OperationRepository {

    private final Context context;
    private final OperationQueue queue;
    private final Executor io;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private final MutableLiveData<List<OperationEntry>> entries = new MutableLiveData<>();

    public OperationRepository(
            @NonNull final Context context,
            @NonNull final OperationQueue queue,
            @NonNull final Executor io
    ) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.queue = Objects.requireNonNull(queue, "queue");
        this.io = Objects.requireNonNull(io, "io");
        entries.setValue(queue.snapshot());
        queue.addListener(changed -> requestRefresh());
    }

    /** Alle Vorgänge, älteste zuerst. */
    @NonNull
    public LiveData<List<OperationEntry>> entries() {
        return entries;
    }

    /** Die Warteschlange selbst, für den Dienst. */
    @NonNull
    OperationQueue queue() {
        return queue;
    }

    /** Reiht den Vorgang ein und sorgt dafür, dass der Vordergrunddienst läuft. */
    public void enqueue(
            @NonNull final Operation operation
    ) {
        Objects.requireNonNull(operation, "operation");
        io.execute(() -> {
            queue.enqueue(operation);
            GitOperationService.start(context);
        });
    }

    public void cancel(
            final long id
    ) {
        io.execute(() -> queue.cancel(id));
    }

    public void cancelAll() {
        io.execute(queue::cancelAll);
    }

    public void retry(
            final long id
    ) {
        io.execute(() -> {
            if (queue.retry(id).isPresent()) {
                GitOperationService.start(context);
            }
        });
    }

    public void clearFinished() {
        io.execute(queue::clearFinished);
    }

    public boolean canRetry(
            final long id
    ) {
        return queue.canRetry(id);
    }

    /** Liest den Stand erst auf dem Hauptthread, damit nie ein älterer Stand einen neueren überschreibt. */
    private void requestRefresh() {
        if (refreshPending.compareAndSet(false, true)) {
            main.post(() -> {
                refreshPending.set(false);
                entries.setValue(queue.snapshot());
            });
        }
    }
}
