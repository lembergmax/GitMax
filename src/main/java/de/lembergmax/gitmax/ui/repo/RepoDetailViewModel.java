package de.lembergmax.gitmax.ui.repo;

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
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.GitLfs;
import de.lembergmax.gitmax.domain.model.ChangedFile;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.CommitRequest;
import de.lembergmax.gitmax.domain.model.LocalRepo;
import de.lembergmax.gitmax.domain.model.PushRequest;
import de.lembergmax.gitmax.domain.model.RepoStatus;
import de.lembergmax.gitmax.domain.model.UpdateRequest;
import de.lembergmax.gitmax.domain.model.WorkingTree;
import de.lembergmax.gitmax.ops.OperationEntry;
import de.lembergmax.gitmax.ops.OperationState;
import de.lembergmax.gitmax.ops.OperationTexts;
import de.lembergmax.gitmax.git.ssh.KnownHostsStore;
import de.lembergmax.gitmax.ops.Operations;
import de.lembergmax.gitmax.repository.RepoRemover;
import de.lembergmax.gitmax.ui.common.Event;

import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Zustand und Aktionen eines einzelnen Repos: Status, Änderungen, Vormerken, Committen, Aktualisieren und
 * Pushen. Zeigt außerdem den laufenden Vorgang dieses Repos und, wenn der letzte scheiterte, den Grund.
 */
public final class RepoDetailViewModel extends AndroidViewModel {

    /**
     * Was der Bildschirm zeichnet.
     *
     * @param name       Ordnername
     * @param path       vollständiger Pfad
     * @param loading    der Zustand wird gerade gelesen
     * @param unreadable das Repo ließ sich nicht lesen
     * @param status     Zustand des Branches, {@code null} bis er gelesen ist
     * @param tree       Änderungen, {@code null} bis sie gelesen sind
     * @param active     wartender oder laufender Vorgang dieses Repos
     * @param failure    zuletzt gescheiterter Vorgang dieses Repos, solange er nicht ausgeblendet wurde
     * @param usesLfs    {@code .gitattributes} bindet Dateien an Git LFS, das GitMax nicht unterstützt
     */
    public record UiState(
            @NonNull String name,
            @NonNull String path,
            boolean loading,
            boolean unreadable,
            @Nullable RepoStatus status,
            @Nullable WorkingTree tree,
            @Nullable OperationEntry active,
            @Nullable OperationEntry failure,
            boolean usesLfs
    ) {
    }

    /**
     * Ausgang eines Commits.
     *
     * @param shortId gekürzte Kennung bei Erfolg
     * @param error   Beschreibung bei Misserfolg
     */
    public record CommitResult(
            @Nullable String shortId,
            @Nullable String error
    ) {
    }

    /** Eine Git-Aktion, die scheitern darf. */
    private interface GitTask {

        void run() throws GitFailureException;
    }

    /** Größer ist keine sinnvolle {@code .gitattributes}; so bleibt das Lesen beim Laden billig. */
    private static final long MAX_ATTRIBUTES_BYTES = 64 * 1024L;

    private final ServiceLocator services;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<CommitResult>> commitResults = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> deletions = new MutableLiveData<>();
    private final Observer<List<OperationEntry>> operationObserver = this::onOperations;

    private File directory;
    private LocalRepo repo;
    private boolean loading = true;
    private boolean unreadable;
    private RepoStatus status;
    private WorkingTree tree;
    private OperationEntry active;
    private OperationEntry failure;
    private boolean usesLfs;
    private long dismissedFailureId;
    private int loadGeneration;

    public RepoDetailViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
    }

    /** Legt das Repo fest; ein zweiter Aufruf (Drehung des Bildschirms) ändert nichts. */
    public void init(
            @NonNull final String path
    ) {
        if (directory != null) {
            return;
        }
        directory = new File(path);
        final File parent = directory.getParentFile();
        repo = new LocalRepo(directory, parent == null ? directory : parent);
        services.operations().entries().observeForever(operationObserver);
        publish();
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
    public LiveData<Event<CommitResult>> commitResults() {
        return commitResults;
    }

    /** Meldet einmal, dass das Repo vom Gerät gelöscht wurde. */
    @NonNull
    public LiveData<Event<Boolean>> deletions() {
        return deletions;
    }

    @NonNull
    public File directory() {
        return directory;
    }

    @Override
    protected void onCleared() {
        services.operations().entries().removeObserver(operationObserver);
    }

    /** Liest Status und Änderungen neu. */
    public void reload() {
        loading = true;
        publish();
        loadGeneration += 1;
        final int generation = loadGeneration;
        services.io().execute(() -> {
            RepoStatus newStatus = null;
            WorkingTree newTree = null;
            boolean failed = false;
            final boolean lfs = readUsesLfs();
            try {
                newStatus = services.localRepos().fullStatus(repo);
                newTree = services.engine().status(directory);
            } catch (final IOException | GitAPIException | GitFailureException | RuntimeException unreadableRepo) {
                failed = true;
            }
            final RepoStatus loadedStatus = newStatus;
            final WorkingTree loadedTree = newTree;
            final boolean loadFailed = failed;
            main.post(() -> {
                // Ein neueres Laden hat Vorrang; dieses Ergebnis ist dann schon veraltet.
                if (generation != loadGeneration) {
                    return;
                }
                status = loadedStatus;
                tree = loadedTree;
                usesLfs = lfs;
                unreadable = loadFailed;
                loading = false;
                publish();
            });
        });
    }

    /** Merkt oder nimmt zurück: wechselt die Vormerkung der Datei. */
    public void toggle(
            @NonNull final ChangedFile file,
            final boolean staged
    ) {
        if (staged) {
            run(() -> services.engine().unstage(directory, List.of(file.path())));
        } else {
            run(() -> services.engine().stage(directory, List.of(file.path())));
        }
    }

    public void stageAll(
            @NonNull final List<ChangedFile> files
    ) {
        run(() -> services.engine().stage(directory, paths(files)));
    }

    public void unstageAll(
            @NonNull final List<ChangedFile> files
    ) {
        run(() -> services.engine().unstage(directory, paths(files)));
    }

    public void discard(
            @NonNull final List<String> paths
    ) {
        run(() -> services.engine().discard(directory, paths));
    }

    /** Aktualisiert das Repo; Konflikte bleiben im Repo stehen, damit man sie lösen kann. */
    public void update(
            final boolean autoStash
    ) {
        services.operations().enqueue(Operations.update(new UpdateRequest(
                directory, services.repoSettings().updateStrategy(directory), UpdateRequest.ConflictPolicy.LEAVE, autoStash)));
    }

    public void fetch() {
        services.operations().enqueue(Operations.fetch(directory));
    }

    /** Pusht; gibt {@code false} zurück, wenn es nichts zu pushen gibt. */
    public boolean push() {
        if (status != null && status.hasUpstream() && status.ahead() == 0) {
            return false;
        }
        services.operations().enqueue(Operations.push(new PushRequest(directory, false, false)));
        return true;
    }

    /** Erzwingt den Push: der Branch auf dem Server wird durch den lokalen Stand ersetzt (mit Lease-Prüfung der Engine). */
    public void forcePush() {
        services.operations().enqueue(Operations.push(new PushRequest(directory, false, true)));
    }

    /** Löscht das Repo samt Arbeitskopie vom Gerät; läuft noch ein Vorgang dafür, geschieht nichts. */
    public void deleteFromDevice() {
        if (active != null) {
            messages.setValue(new Event<>(getApplication().getString(R.string.repo_delete_busy)));
            return;
        }
        services.io().execute(() -> {
            String error = null;
            try {
                services.repoRemover().delete(directory);
            } catch (final RepoRemover.RemovalException failed) {
                error = getApplication().getString(failed.reason() == RepoRemover.Reason.WORKSPACE_ROOT
                        ? R.string.repo_delete_root : R.string.repo_delete_failed);
            }
            final String failure = error;
            main.post(() -> {
                if (failure == null) {
                    deletions.setValue(new Event<>(Boolean.TRUE));
                } else {
                    messages.setValue(new Event<>(failure));
                    reload();
                }
            });
        });
    }

    public void abortMerge() {
        run(() -> services.engine().abortMerge(directory));
    }

    /** Die SSH-Server, denen die App vertraut; die Oberfläche zeigt damit den Fingerabdruck eines gescheiterten Vorgangs. */
    @NonNull
    public KnownHostsStore knownHosts() {
        return services.knownHosts();
    }

    /** Stellt einen gescheiterten Vorgang erneut ein. */
    public void retry(
            @NonNull final OperationEntry entry
    ) {
        services.operations().retry(entry.id());
    }

    public void dismissFailure() {
        if (failure != null) {
            dismissedFailureId = failure.id();
            failure = null;
            publish();
        }
    }

    /** Die Identität für Commits in diesem Repo, falls eine bestimmbar ist. */
    @NonNull
    public Optional<CommitIdentity> identity() {
        return services.identities().resolve(directory);
    }

    public void chooseIdentity(
            @NonNull final CommitIdentity identity
    ) {
        services.identities().choose(directory, identity);
    }

    /**
     * Committet; bei {@code stageAll} wird vorher alles vorgemerkt, bei {@code thenPush} danach gepusht. Das
     * Ergebnis kommt über {@link #commitResults()}.
     */
    public void commit(
            @NonNull final String message,
            @NonNull final CommitIdentity identity,
            final boolean amend,
            final boolean stageAll,
            final boolean thenPush
    ) {
        services.io().execute(() -> {
            CommitResult result;
            try {
                if (stageAll) {
                    final WorkingTree current = services.engine().status(directory);
                    final List<String> toStage = new ArrayList<>();
                    current.unstaged().forEach(file -> toStage.add(file.path()));
                    current.untracked().forEach(file -> toStage.add(file.path()));
                    services.engine().stage(directory, toStage);
                }
                final String id = services.engine().commit(new CommitRequest(directory, message, identity, amend));
                result = new CommitResult(id, null);
                if (thenPush) {
                    services.operations().enqueue(Operations.push(new PushRequest(directory, false, false)));
                }
            } catch (final GitFailureException failed) {
                result = new CommitResult(null, describe(failed));
            }
            final CommitResult outcome = result;
            main.post(() -> commitResults.setValue(new Event<>(outcome)));
            reloadFromBackground();
        });
    }

    /* ------------------------------------------------------------------------------------------ */

    private void run(
            @NonNull final GitTask task
    ) {
        services.io().execute(() -> {
            try {
                task.run();
            } catch (final GitFailureException failed) {
                main.post(() -> messages.setValue(new Event<>(describe(failed))));
            }
            reloadFromBackground();
        });
    }

    /** Ob {@code .gitattributes} im Wurzelordner irgendein Muster an den LFS-Filter bindet. */
    private boolean readUsesLfs() {
        final File attributes = new File(directory, ".gitattributes");
        if (!attributes.isFile() || attributes.length() > MAX_ATTRIBUTES_BYTES) {
            return false;
        }
        try {
            return GitLfs.usesLfs(new String(Files.readAllBytes(attributes.toPath()), StandardCharsets.UTF_8));
        } catch (final IOException unreadable) {
            return false;
        }
    }

    private void reloadFromBackground() {
        main.post(this::reload);
    }

    private String describe(
            @NonNull final GitFailureException failed
    ) {
        return OperationTexts.failure(getApplication(),
                new OperationEntry.Failure(failed.kind(), failed.getMessage(), failed.paths()));
    }

    private static List<String> paths(
            final List<ChangedFile> files
    ) {
        final List<String> paths = new ArrayList<>(files.size());
        for (final ChangedFile file : files) {
            paths.add(file.path());
        }
        return paths;
    }

    private void onOperations(
            @NonNull final List<OperationEntry> entries
    ) {
        final String own = directory.getAbsolutePath();
        OperationEntry running = null;
        OperationEntry queued = null;
        OperationEntry latestFinished = null;
        for (final OperationEntry entry : entries) {
            if (!entry.directory().equals(own)) {
                continue;
            }
            if (entry.state() == OperationState.RUNNING) {
                running = entry;
            } else if (entry.state() == OperationState.QUEUED) {
                queued = entry;
            } else if (latestFinished == null || entry.id() > latestFinished.id()) {
                latestFinished = entry;
            }
        }
        // Ein Fehler gilt nur, solange nichts Neueres für dieses Repo beendet wurde.
        final OperationEntry lastFailure = latestFinished != null
                && latestFinished.state() == OperationState.FAILED
                && latestFinished.id() > dismissedFailureId ? latestFinished : null;
        final OperationEntry nowActive = running != null ? running : queued;
        final boolean justFinished = active != null && nowActive == null;
        active = nowActive;
        failure = lastFailure;
        publish();
        if (justFinished) {
            reload();
        }
    }

    private void publish() {
        state.setValue(new UiState(directory.getName(), directory.getAbsolutePath(), loading, unreadable,
                status, tree, active, active == null ? failure : null, usesLfs));
    }
}
