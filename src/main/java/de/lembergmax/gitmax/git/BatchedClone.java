package de.lembergmax.gitmax.git;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.BuildConfig;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.GitFailureKind;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.FilterSpec;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Klont in kleinen, einzeln gesicherten Schritten statt in einer einzigen Antwort.
 * <p>
 * Ein normaler Klon ist <em>eine</em> HTTP-Antwort. Bei einem großen Repo dauert sie Stunden, und Server, Proxys oder VPNs
 * kappen so lange Verbindungen (auf dem Testserver nach etwa fünf Minuten). Dann ist alles bisher Geladene verloren. Hier
 * holt der erste Schritt nur Commits und Verzeichnisse ({@code blob:none}, wenige Sekunden), danach kommen die Dateiinhalte in
 * Paketen, deren Größe sich an der Dauer ausrichtet. Jedes fertige Paket bleibt liegen. Reißt die Verbindung, wird nur das
 * laufende Paket wiederholt, und was schon da ist, wird nicht noch einmal geladen.
 * <p>
 * Verträgt der Server keine Filter oder keine Anforderung einzelner Objekte, liefert {@link #run()} {@link Optional#empty()};
 * der Aufrufer fällt dann auf den normalen Klon zurück. Ein Abruf „den Rest am Stück“ gäbe es nicht: Wer die Commits schon
 * hat, bekommt vom Server deren Dateiinhalte nicht noch einmal, auch wenn sie fehlen.
 */
final class BatchedClone {

    private static final String ORIGIN = "origin";
    private static final String HEADS_TO_ORIGIN = "+refs/heads/*:refs/remotes/origin/*";
    private static final String TAGS = "+refs/tags/*:refs/tags/*";
    private static final String NO_BLOBS = "blob:none";

    static final int FIRST_BATCH_OBJECTS = 200;
    private static final int MIN_BATCH_OBJECTS = 10;
    private static final int MAX_BATCH_OBJECTS = 5_000;
    // Ein Schritt soll deutlich unter der Zeit enden, nach der Verbindungen gekappt werden.
    private static final long FAST_BATCH_MS = 30_000L;
    private static final long SLOW_BATCH_MS = 120_000L;
    // Ein Schritt soll nicht mehr als etwa so viel Daten bringen: der Server meldet sich bei großen Paketen minutenlang nicht.
    private static final long TARGET_BATCH_BYTES = 200L * 1024 * 1024;
    private static final int MAX_FAILED_ATTEMPTS = 8;
    private static final long RETRY_PAUSE_MS = 4_000L;

    private final String uri;
    private final File target;
    @Nullable
    private final String branch;
    private final boolean shallow;
    @Nullable
    private final CredentialsProvider credentials;
    @Nullable
    private final TransportConfigCallback callback;
    private final int timeoutSeconds;
    private final ProgressAdapter monitor;
    private final int firstBatchObjects;
    private final long retryPauseMs;

    BatchedClone(
            @NonNull final String uri,
            @NonNull final File target,
            @Nullable final String branch,
            final boolean shallow,
            @Nullable final CredentialsProvider credentials,
            @Nullable final TransportConfigCallback callback,
            final int timeoutSeconds,
            @NonNull final ProgressAdapter monitor
    ) {
        this(uri, target, branch, shallow, credentials, callback, timeoutSeconds, monitor, FIRST_BATCH_OBJECTS, RETRY_PAUSE_MS);
    }

    BatchedClone(
            final String uri,
            final File target,
            @Nullable final String branch,
            final boolean shallow,
            @Nullable final CredentialsProvider credentials,
            @Nullable final TransportConfigCallback callback,
            final int timeoutSeconds,
            final ProgressAdapter monitor,
            final int firstBatchObjects,
            final long retryPauseMs
    ) {
        this.uri = uri;
        this.target = target;
        this.branch = branch;
        this.shallow = shallow;
        this.credentials = credentials;
        this.callback = callback;
        this.timeoutSeconds = timeoutSeconds;
        this.monitor = monitor;
        this.firstBatchObjects = firstBatchObjects;
        this.retryPauseMs = retryPauseMs;
    }

    /**
     * @return das geklonte Repo ohne ausgechecktes Arbeitsverzeichnis, oder leer, wenn der Server keine Filter oder keine
     *         einzelnen Objekte liefert (der Zielordner enthält dann Reste und muss vom Aufrufer geleert werden)
     */
    @NonNull
    Optional<Git> run() throws IOException, GitAPIException, GitFailureException, URISyntaxException {
        final Git git = Git.init().setDirectory(target).call();
        boolean done = false;
        try {
            final Repository repo = git.getRepository();
            configureRemote(repo);
            final FetchResult tips = fetchTips(repo);
            if (tips == null) {
                return Optional.empty();
            }
            if (!fetchContents(repo)) {
                return Optional.empty();
            }
            // Erst jetzt zeigt HEAD auf den Branch: ein halber Klon soll nicht als Repo mit lauter gelöschten Dateien erscheinen.
            checkOutBranchPointer(repo, tips);
            done = true;
            return Optional.of(git);
        } finally {
            if (!done) {
                git.close();
            }
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Schritt 1: Commits und Verzeichnisse                                                       */
    /* ------------------------------------------------------------------------------------------ */

    private void configureRemote(
            final Repository repo
    ) throws IOException, URISyntaxException {
        final StoredConfig config = repo.getConfig();
        final RemoteConfig remote = new RemoteConfig(config, ORIGIN);
        remote.addURI(new URIish(uri));
        remote.addFetchRefSpec(new RefSpec(HEADS_TO_ORIGIN));
        remote.update(config);
        config.save();
    }

    @Nullable
    private FetchResult fetchTips(
            final Repository repo
    ) throws IOException, GitFailureException {
        for (int attempt = 1; ; attempt++) {
            try (Transport transport = open(repo)) {
                transport.setFilterSpec(FilterSpec.fromFilterLine(NO_BLOBS));
                transport.setTagOpt(TagOpt.AUTO_FOLLOW);
                if (shallow) {
                    transport.setDepth(1);
                }
                return transport.fetch(monitor, tipSpecs());
            } catch (final IOException failure) {
                if (rejectsFilter(failure)) {
                    return null;
                }
                retryOrThrow(failure, attempt);
            }
        }
    }

    private List<RefSpec> tipSpecs() {
        final List<RefSpec> specs = new ArrayList<>();
        if (branch != null) {
            specs.add(new RefSpec("+" + Constants.R_HEADS + branch + ":" + Constants.R_REMOTES + ORIGIN + "/" + branch));
        } else {
            specs.add(new RefSpec(HEADS_TO_ORIGIN));
            specs.add(new RefSpec(TAGS));
        }
        return specs;
    }

    /** Legt den lokalen Branch an, den ein normaler Klon auschecken würde, und zeigt {@code HEAD} darauf. */
    private void checkOutBranchPointer(
            final Repository repo,
            final FetchResult tips
    ) throws IOException {
        final String name = branchToCheckOut(repo, tips);
        if (name == null) {
            return;
        }
        final Ref remote = repo.exactRef(Constants.R_REMOTES + ORIGIN + "/" + name);
        if (remote == null || remote.getObjectId() == null) {
            return;
        }
        final RefUpdate update = repo.updateRef(Constants.R_HEADS + name);
        update.setNewObjectId(remote.getObjectId());
        update.update();
        repo.updateRef(Constants.HEAD).link(Constants.R_HEADS + name);
        final StoredConfig config = repo.getConfig();
        config.setString("branch", name, "remote", ORIGIN);
        config.setString("branch", name, "merge", Constants.R_HEADS + name);
        config.save();
    }

    @Nullable
    private String branchToCheckOut(
            final Repository repo,
            final FetchResult tips
    ) throws IOException {
        if (branch != null) {
            return branch;
        }
        final Ref head = tips.getAdvertisedRef(Constants.HEAD);
        if (head == null) {
            return null;
        }
        if (head.isSymbolic()) {
            return Repository.shortenRefName(head.getTarget().getName());
        }
        // Ohne symbolischen HEAD: ein Branch, der auf denselben Commit zeigt (wie bei einem normalen Klon).
        String fallback = null;
        for (final Ref candidate : repo.getRefDatabase().getRefsByPrefix(Constants.R_REMOTES + ORIGIN + "/")) {
            if (head.getObjectId().equals(candidate.getObjectId())) {
                final String name = candidate.getName().substring((Constants.R_REMOTES + ORIGIN + "/").length());
                if ("main".equals(name) || "master".equals(name)) {
                    return name;
                }
                fallback = fallback == null ? name : fallback;
            }
        }
        return fallback;
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Schritt 2: Dateiinhalte in Paketen                                                         */
    /* ------------------------------------------------------------------------------------------ */

    /** @return {@code false}, wenn der Server die einzelnen Objekte nicht liefert oder nach dem Abruf welche fehlen */
    private boolean fetchContents(
            final Repository repo
    ) throws IOException, GitFailureException {
        final List<ObjectId> missing = missingBlobs(repo);
        if (missing.isEmpty()) {
            return true;
        }
        monitor.beginTask("Receiving objects", missing.size());
        int batchSize = firstBatchObjects;
        int index = 0;
        int failures = 0;
        double bytesPerObject = 0;
        while (index < missing.size()) {
            checkNotCancelled();
            final int end = Math.min(missing.size(), index + batchSize);
            final List<ObjectId> slice = missing.subList(index, end);
            final long started = System.currentTimeMillis();
            final long packBytesBefore = packBytes(repo);
            try {
                fetchObjects(repo, slice);
            } catch (final IOException failure) {
                if (rejectsWants(failure)) {
                    return false;
                }
                failures++;
                retryOrThrow(failure, failures);
                batchSize = Math.max(MIN_BATCH_OBJECTS, batchSize / 2);
                continue;
            }
            failures = 0;
            index = end;
            monitor.update(slice.size());
            final long tookMs = System.currentTimeMillis() - started;
            final double perObject = (double) (packBytes(repo) - packBytesBefore) / slice.size();
            bytesPerObject = bytesPerObject == 0 ? perObject : (bytesPerObject + perObject) / 2;
            batchSize = Math.min(nextBatchSize(batchSize, tookMs), objectsForBytes(bytesPerObject));
            if (BuildConfig.TEST_BUILD) {
                Log.i("GitMaxBatch", slice.size() + " Objekte, " + (long) (perObject * slice.size() / 1024) + " KiB in " + tookMs
                        + " ms, naechster Schritt " + batchSize);
            }
        }
        // Hat der Server etwas ausgelassen, hilft nur der normale Klon des Aufrufers.
        return missingBlobs(repo).isEmpty();
    }

    private static int objectsForBytes(
            final double bytesPerObject
    ) {
        if (bytesPerObject <= 0) {
            return MAX_BATCH_OBJECTS;
        }
        return (int) Math.max(MIN_BATCH_OBJECTS, Math.min(MAX_BATCH_OBJECTS, TARGET_BATCH_BYTES / bytesPerObject));
    }

    private static long packBytes(
            final Repository repo
    ) {
        final File[] packs = new File(repo.getDirectory(), "objects/pack").listFiles((dir, name) -> name.endsWith(".pack"));
        long total = 0;
        if (packs != null) {
            for (final File pack : packs) {
                total += pack.length();
            }
        }
        return total;
    }

    private static int nextBatchSize(
            final int current,
            final long tookMs
    ) {
        if (tookMs < FAST_BATCH_MS) {
            return Math.min(MAX_BATCH_OBJECTS, current * 2);
        }
        if (tookMs > SLOW_BATCH_MS) {
            return Math.max(MIN_BATCH_OBJECTS, current / 2);
        }
        return current;
    }

    /** Alle Dateiinhalte, die zu den geholten Commits gehören und noch fehlen. */
    private List<ObjectId> missingBlobs(
            final Repository repo
    ) throws IOException {
        final List<ObjectId> missing = new ArrayList<>();
        try (ObjectWalk walk = new ObjectWalk(repo)) {
            walk.setRetainBody(false);
            boolean any = false;
            for (final String prefix : new String[] {Constants.R_REMOTES + ORIGIN + "/", Constants.R_TAGS}) {
                for (final Ref ref : repo.getRefDatabase().getRefsByPrefix(prefix)) {
                    any |= markStart(walk, ref);
                }
            }
            if (!any) {
                return missing;
            }
            RevCommit commit;
            do {
                commit = walk.next();
            } while (commit != null);
            RevObject object;
            while ((object = walk.nextObject()) != null) {
                if (object.getType() == Constants.OBJ_BLOB && !repo.getObjectDatabase().has(object)) {
                    missing.add(object.copy());
                }
            }
        }
        return missing;
    }

    private static boolean markStart(
            final ObjectWalk walk,
            final Ref ref
    ) throws IOException {
        if (ref.getObjectId() == null) {
            return false;
        }
        try {
            walk.markStart(walk.parseCommit(ref.getObjectId()));
            return true;
        } catch (final IncorrectObjectTypeException notACommit) {
            // Ein Tag auf einen Baum oder Blob zählt hier nicht.
            return false;
        } catch (final MissingObjectException shallowBoundary) {
            return false;
        }
    }

    private void fetchObjects(
            final Repository repo,
            final List<ObjectId> ids
    ) throws IOException {
        final List<RefSpec> specs = new ArrayList<>(ids.size());
        for (final ObjectId id : ids) {
            specs.add(new RefSpec(id.name()));
        }
        try (Transport transport = open(repo)) {
            transport.fetch(cancelOnly(), specs);
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Hilfen                                                                                     */
    /* ------------------------------------------------------------------------------------------ */

    private Transport open(
            final Repository repo
    ) throws IOException {
        final Transport transport;
        try {
            transport = Transport.open(repo, new URIish(uri));
        } catch (final URISyntaxException invalid) {
            throw new IOException("Invalid remote address", invalid);
        }
        transport.setCredentialsProvider(credentials);
        transport.setTimeout(timeoutSeconds);
        if (callback != null) {
            callback.configure(transport);
        }
        return transport;
    }

    /** Ein Monitor, der nur den Abbruch weitergibt; den Fortschritt über alle Pakete meldet {@link #fetchContents}. */
    private ProgressMonitor cancelOnly() {
        return new ProgressMonitor() {
            @Override
            public void start(
                    final int totalTasks
            ) {
                // Nur der Abbruch zählt.
            }

            @Override
            public void beginTask(
                    final String title,
                    final int totalWork
            ) {
                // Nur der Abbruch zählt.
            }

            @Override
            public void update(
                    final int completed
            ) {
                // Nur der Abbruch zählt.
            }

            @Override
            public void endTask() {
                // Nur der Abbruch zählt.
            }

            @Override
            public boolean isCancelled() {
                return monitor.isCancelled();
            }

            @Override
            public void showDuration(
                    final boolean enabled
            ) {
                // Nur der Abbruch zählt.
            }
        };
    }

    private void checkNotCancelled() throws GitFailureException {
        if (monitor.isCancelled()) {
            throw new GitFailureException(GitFailureKind.CANCELLED, "Cancelled", null);
        }
    }

    /**
     * Wiederholt nach einer Pause, solange es nur an der Verbindung lag; sonst (oder nach zu vielen Fehlversuchen
     * ohne Fortschritt) wird der Fehler weitergegeben.
     */
    private void retryOrThrow(
            final IOException failure,
            final int failedAttempts
    ) throws IOException, GitFailureException {
        checkNotCancelled();
        final GitFailureException mapped = GitErrorMapper.map(failure, false);
        if (mapped.kind() != GitFailureKind.NETWORK || failedAttempts >= MAX_FAILED_ATTEMPTS) {
            throw failure;
        }
        try {
            Thread.sleep(retryPauseMs * failedAttempts);
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GitFailureException(GitFailureKind.CANCELLED, "Interrupted while waiting to retry", interrupted);
        }
    }

    private static boolean rejectsFilter(
            final IOException failure
    ) {
        return mentions(failure, "filter");
    }

    private static boolean rejectsWants(
            final IOException failure
    ) {
        return mentions(failure, "not our ref") || mentions(failure, "unadvertised") || mentions(failure, "not allowed")
                || mentions(failure, "no such ref") || mentions(failure, "not valid");
    }

    private static boolean mentions(
            final Throwable failure,
            final String text
    ) {
        Throwable current = failure;
        while (current != null) {
            final String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(text)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
