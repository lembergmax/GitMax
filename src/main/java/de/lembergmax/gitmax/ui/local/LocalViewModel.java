package de.lembergmax.gitmax.ui.local;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.model.LocalRepo;
import de.lembergmax.gitmax.domain.model.PushRequest;
import de.lembergmax.gitmax.domain.model.RepoStatus;
import de.lembergmax.gitmax.domain.model.UpdateRequest;
import de.lembergmax.gitmax.ops.OperationEntry;
import de.lembergmax.gitmax.ops.OperationState;
import de.lembergmax.gitmax.ops.Operations;
import de.lembergmax.gitmax.storage.StoragePermission;
import de.lembergmax.gitmax.ui.common.Event;

import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lädt die lokalen Repos: erst die Liste samt schnellem Zustand, dann nach und nach die langsame
 * Dateiprüfung je Repo. Ein neuer Ladevorgang macht einen noch laufenden hinfällig. Verwaltet außerdem
 * Filter, Suche, Auswahl und die Sammelaktionen (Aktualisieren, Pushen, Abrufen). Der Zustand wird nur
 * auf dem Hauptthread verändert.
 */
public final class LocalViewModel extends AndroidViewModel {

    /** Was der Bildschirm gerade zeigt. */
    public enum Phase {
        NEEDS_PERMISSION,
        NEEDS_WORKSPACE,
        LOADING,
        EMPTY,
        LIST
    }

    /** Eingrenzung der Liste nach Zustand. */
    public enum Filter {
        ALL,
        CHANGED,
        AHEAD,
        BEHIND,
        CONFLICT
    }

    /**
     * Eine Zeile der Liste.
     *
     * @param repo       das Repo
     * @param status     Zustand, {@code null} solange er nicht gelesen werden konnte
     * @param unreadable {@code true}, wenn das Lesen fehlschlug (kaputtes oder gesperrtes Repo)
     * @param selected   {@code true}, wenn es für eine Sammelaktion markiert ist
     * @param active     wartender oder laufender Vorgang dieses Repos
     */
    public record Row(
            @NonNull LocalRepo repo,
            @Nullable RepoStatus status,
            boolean unreadable,
            boolean selected,
            @Nullable OperationEntry active
    ) {

        /** Schlüssel für Auswahl und Vergleich: der Pfad des Repos. */
        @NonNull
        public String key() {
            return repo.directory().getAbsolutePath();
        }
    }

    /**
     * Die sichtbare Liste.
     *
     * @param rows          Zeilen nach Filter und Suche
     * @param selectedCount Zahl der markierten Repos
     * @param filter        gewählter Filter
     * @param counts        Zahl der Repos je Filter (ohne Suche)
     * @param total         Zahl aller Repos
     */
    public record ListState(
            @NonNull List<Row> rows,
            int selectedCount,
            @NonNull Filter filter,
            @NonNull Map<Filter, Integer> counts,
            int total
    ) {
    }

    private static final String DEFAULT_FOLDER_NAME = "GitMax";

    private final ServiceLocator services;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<Phase> phase = new MutableLiveData<>(Phase.LOADING);
    private final MutableLiveData<ListState> list = new MutableLiveData<>(
            new ListState(List.of(), 0, Filter.ALL, new EnumMap<>(Filter.class), 0));
    private final MutableLiveData<File> defaultRoot = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final AtomicInteger generation = new AtomicInteger();
    private final Observer<List<OperationEntry>> operationObserver = this::onOperations;

    private List<Row> allRows = List.of();
    private final Set<String> selection = new LinkedHashSet<>();
    private final Map<String, OperationEntry> activeByPath = new HashMap<>();
    private Filter filter = Filter.ALL;
    private String query = "";

    public LocalViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
        services.operations().entries().observeForever(operationObserver);
    }

    @Override
    protected void onCleared() {
        services.operations().entries().removeObserver(operationObserver);
    }

    @NonNull
    public LiveData<Phase> phase() {
        return phase;
    }

    @NonNull
    public LiveData<ListState> list() {
        return list;
    }

    /** Standard-Zielordner, für Texte im Leerzustand. */
    @NonNull
    public LiveData<File> defaultRoot() {
        return defaultRoot;
    }

    /** Einmalige Fehlermeldungen. */
    @NonNull
    public LiveData<Event<String>> messages() {
        return messages;
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Filter, Suche, Auswahl                                                                     */
    /* ------------------------------------------------------------------------------------------ */

    public void setFilter(
            @NonNull final Filter newFilter
    ) {
        filter = newFilter;
        publish();
    }

    public void setQuery(
            @NonNull final String newQuery
    ) {
        query = newQuery.trim();
        publish();
    }

    public void toggleSelection(
            @NonNull final String key
    ) {
        if (!selection.remove(key)) {
            selection.add(key);
        }
        publish();
    }

    public void clearSelection() {
        selection.clear();
        publish();
    }

    public void selectAllVisible() {
        final ListState current = list.getValue();
        if (current == null) {
            return;
        }
        for (final Row row : current.rows()) {
            selection.add(row.key());
        }
        publish();
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Sammelaktionen                                                                             */
    /* ------------------------------------------------------------------------------------------ */

    /**
     * Aktualisiert die markierten Repos. Konflikte brechen nur das betroffene Repo sauber ab, die übrigen
     * laufen weiter.
     *
     * @return Zahl der eingereihten Vorgänge
     */
    public int updateSelected() {
        return enqueueUpdates(selectedRows(), false);
    }

    /** Aktualisiert alle Repos, die dafür in Frage kommen: mit Upstream, nicht losgelöst, ohne Konflikt. */
    public int updateAll() {
        return enqueueUpdates(allRows, true);
    }

    /** Pusht die markierten Repos, die lokale Commits haben oder noch keinen Upstream. */
    public int pushSelected() {
        int started = 0;
        for (final Row row : selectedRows()) {
            final RepoStatus status = row.status();
            if (activeByPath.containsKey(row.key()) || status == null || status.detached() || status.conflicted()
                    || (status.hasUpstream() && status.ahead() == 0)) {
                continue;
            }
            services.operations().enqueue(Operations.push(new PushRequest(row.repo().directory(), false, false)));
            started += 1;
        }
        clearSelection();
        return started;
    }

    /** Ruft die markierten Repos ab, ohne ihre Arbeitskopie zu verändern. */
    public int fetchSelected() {
        int started = 0;
        for (final Row row : selectedRows()) {
            if (!activeByPath.containsKey(row.key()) && !row.unreadable()) {
                services.operations().enqueue(Operations.fetch(row.repo().directory()));
                started += 1;
            }
        }
        clearSelection();
        return started;
    }

    private int enqueueUpdates(
            final List<Row> rows,
            final boolean onlyEligible
    ) {
        int started = 0;
        for (final Row row : rows) {
            final RepoStatus status = row.status();
            if (activeByPath.containsKey(row.key()) || row.unreadable()) {
                continue;
            }
            if (onlyEligible && (status == null || !status.hasUpstream() || status.detached() || status.conflicted())) {
                continue;
            }
            services.operations().enqueue(Operations.update(new UpdateRequest(row.repo().directory(),
                    services.repoSettings().updateStrategy(row.repo().directory()), UpdateRequest.ConflictPolicy.ABORT, false)));
            started += 1;
        }
        clearSelection();
        return started;
    }

    private List<Row> selectedRows() {
        final List<Row> rows = new ArrayList<>();
        for (final Row row : allRows) {
            if (selection.contains(row.key())) {
                rows.add(row);
            }
        }
        return rows;
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Laden                                                                                      */
    /* ------------------------------------------------------------------------------------------ */

    /** Legt den Ordner „GitMax“ im Telefonspeicher an und macht ihn zum Arbeitsordner. */
    public void createDefaultWorkspace() {
        services.io().execute(() -> {
            final File folder = new File(StoragePermission.primaryStorageRoot(), DEFAULT_FOLDER_NAME);
            if (!folder.isDirectory() && !folder.mkdirs()) {
                messages.postValue(new Event<>(getApplication().getString(R.string.local_workspace_create_failed)));
                return;
            }
            services.workspace().addRoot(folder);
            refreshFromBackground();
        });
    }

    /** Nimmt einen gewählten Ordner als Arbeitsordner auf. */
    public void addWorkspace(
            @NonNull final File folder
    ) {
        services.io().execute(() -> {
            services.workspace().addRoot(folder);
            refreshFromBackground();
        });
    }

    /** Lädt alles neu. */
    public void refresh() {
        refresh(false);
    }

    /** Lädt neu, ohne die Liste durch den Ladezustand zu ersetzen (nach beendeten Vorgängen). */
    private void refresh(
            final boolean quietly
    ) {
        final int current = generation.incrementAndGet();
        if (!StoragePermission.isGranted()) {
            phase.setValue(Phase.NEEDS_PERMISSION);
            return;
        }
        if (services.workspace().roots().isEmpty()) {
            phase.setValue(Phase.NEEDS_WORKSPACE);
            return;
        }
        final Optional<File> target = services.workspace().defaultTarget();
        defaultRoot.setValue(target.orElse(null));
        if (!quietly) {
            phase.setValue(Phase.LOADING);
        }

        services.io().execute(() -> load(current));
    }

    /** {@link #refresh()} ist für den Haupt-Thread gedacht; von einem Hintergrund-Thread aus dorthin wechseln. */
    private void refreshFromBackground() {
        main.post(this::refresh);
    }

    private void load(
            final int current
    ) {
        final List<LocalRepo> repos = services.localRepos().scan(() -> generation.get() != current);
        if (generation.get() != current) {
            return;
        }
        final List<Row> loaded = new ArrayList<>();
        for (final LocalRepo repo : repos) {
            loaded.add(quickRow(repo));
        }
        main.post(() -> {
            if (generation.get() == current) {
                setRows(loaded);
                phase.setValue(loaded.isEmpty() ? Phase.EMPTY : Phase.LIST);
            }
        });

        for (int index = 0; index < loaded.size() && generation.get() == current; index += 1) {
            final Row full = fullRow(loaded.get(index));
            loaded.set(index, full);
            final List<Row> snapshot = new ArrayList<>(loaded);
            main.post(() -> {
                if (generation.get() == current) {
                    setRows(snapshot);
                }
            });
        }
    }

    private void setRows(
            final List<Row> rows
    ) {
        allRows = rows;
        final Set<String> present = new LinkedHashSet<>();
        for (final Row row : rows) {
            present.add(row.key());
        }
        selection.retainAll(present);
        publish();
    }

    private Row quickRow(
            final LocalRepo repo
    ) {
        try {
            return new Row(repo, services.localRepos().quickStatus(repo), false, false, null);
        } catch (final IOException | RuntimeException unreadable) {
            return new Row(repo, null, true, false, null);
        }
    }

    private Row fullRow(
            final Row quick
    ) {
        if (quick.unreadable()) {
            return quick;
        }
        try {
            return new Row(quick.repo(), services.localRepos().fullStatus(quick.repo()), false, false, null);
        } catch (final IOException | GitAPIException | RuntimeException unreadable) {
            return quick;
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Vorgänge                                                                                   */
    /* ------------------------------------------------------------------------------------------ */

    private void onOperations(
            @NonNull final List<OperationEntry> entries
    ) {
        final Map<String, OperationEntry> now = new HashMap<>();
        for (final OperationEntry entry : entries) {
            if (entry.state() == OperationState.RUNNING
                    || (entry.state() == OperationState.QUEUED && !now.containsKey(entry.directory()))) {
                now.put(entry.directory(), entry);
            }
        }
        final boolean someFinished = !activeByPath.isEmpty() && !now.keySet().containsAll(activeByPath.keySet());
        activeByPath.clear();
        activeByPath.putAll(now);
        publish();
        if (someFinished && phase.getValue() != Phase.LOADING) {
            refresh(true);
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Veröffentlichen                                                                            */
    /* ------------------------------------------------------------------------------------------ */

    private void publish() {
        final Map<Filter, Integer> counts = new EnumMap<>(Filter.class);
        for (final Filter each : Filter.values()) {
            counts.put(each, 0);
        }
        final List<Row> visible = new ArrayList<>();
        final String needle = query.toLowerCase(Locale.ROOT);
        for (final Row base : allRows) {
            final Row row = new Row(base.repo(), base.status(), base.unreadable(),
                    selection.contains(base.key()), activeByPath.get(base.key()));
            for (final Filter each : Filter.values()) {
                if (matches(row, each)) {
                    counts.merge(each, 1, Integer::sum);
                }
            }
            if (matches(row, filter) && (needle.isEmpty()
                    || row.repo().relativePath().toLowerCase(Locale.ROOT).contains(needle))) {
                visible.add(row);
            }
        }
        list.setValue(new ListState(visible, selection.size(), filter, counts, allRows.size()));
    }

    private static boolean matches(
            final Row row,
            final Filter filter
    ) {
        final RepoStatus status = row.status();
        switch (filter) {
            case CHANGED:
                return status != null && status.changesKnown() && status.changed() > 0;
            case AHEAD:
                return status != null && status.ahead() > 0;
            case BEHIND:
                return status != null && status.behind() > 0;
            case CONFLICT:
                return status != null && status.conflicted();
            case ALL:
            default:
                return true;
        }
    }
}
