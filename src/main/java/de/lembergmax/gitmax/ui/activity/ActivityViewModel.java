package de.lembergmax.gitmax.ui.activity;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.ops.OperationEntry;
import de.lembergmax.gitmax.ops.OperationRepository;
import de.lembergmax.gitmax.ops.OperationState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bereitet die Vorgänge für die Aktivitätsliste auf: oben, was läuft oder wartet, darunter das
 * Beendete, neueste zuerst.
 */
public final class ActivityViewModel extends AndroidViewModel {

    /**
     * Eine Zeile der Liste: entweder eine Überschrift oder ein Vorgang.
     *
     * @param headerTitle Text der Überschrift, 0 bei einem Vorgang
     * @param entry       der Vorgang, {@code null} bei einer Überschrift
     * @param retryable   {@code true}, wenn der Vorgang erneut eingestellt werden kann
     */
    public record Item(
            @StringRes int headerTitle,
            @Nullable OperationEntry entry,
            boolean retryable
    ) {

        static Item header(
                @StringRes final int title
        ) {
            return new Item(title, null, false);
        }

        static Item operation(
                @NonNull final OperationEntry entry,
                final boolean retryable
        ) {
            return new Item(0, entry, retryable);
        }

        public boolean isHeader() {
            return entry == null;
        }
    }

    private final OperationRepository operations;
    private final LiveData<List<Item>> items;
    private final LiveData<Boolean> hasActive;
    private final LiveData<Boolean> hasFinished;

    public ActivityViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.operations = ServiceLocator.from(application).operations();
        this.items = Transformations.map(operations.entries(), this::toItems);
        this.hasActive = Transformations.map(operations.entries(), entries ->
                entries.stream().anyMatch(entry -> !entry.state().isFinished()));
        this.hasFinished = Transformations.map(operations.entries(), entries ->
                entries.stream().anyMatch(entry -> entry.state().isFinished()));
    }

    @NonNull
    public LiveData<List<Item>> items() {
        return items;
    }

    @NonNull
    public LiveData<Boolean> hasActive() {
        return hasActive;
    }

    @NonNull
    public LiveData<Boolean> hasFinished() {
        return hasFinished;
    }

    public void cancel(
            @NonNull final OperationEntry entry
    ) {
        operations.cancel(entry.id());
    }

    public void cancelAll() {
        operations.cancelAll();
    }

    public void retry(
            @NonNull final OperationEntry entry
    ) {
        operations.retry(entry.id());
    }

    public void clearFinished() {
        operations.clearFinished();
    }

    private List<Item> toItems(
            @NonNull final List<OperationEntry> entries
    ) {
        final List<OperationEntry> active = new ArrayList<>();
        final List<OperationEntry> finished = new ArrayList<>();
        for (final OperationEntry entry : entries) {
            (entry.state().isFinished() ? finished : active).add(entry);
        }
        active.sort(Comparator.comparing((OperationEntry entry) -> entry.state() == OperationState.RUNNING ? 0 : 1)
                .thenComparingLong(OperationEntry::id));
        finished.sort(Comparator.comparingLong(OperationEntry::id).reversed());

        final List<Item> result = new ArrayList<>();
        if (!active.isEmpty()) {
            result.add(Item.header(R.string.activity_section_active));
            for (final OperationEntry entry : active) {
                result.add(Item.operation(entry, false));
            }
        }
        if (!finished.isEmpty()) {
            result.add(Item.header(R.string.activity_section_recent));
            for (final OperationEntry entry : finished) {
                result.add(Item.operation(entry, operations.canRetry(entry.id())));
            }
        }
        return result;
    }
}
