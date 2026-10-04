package de.lembergmax.gitmax.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.CloneRequest;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.PushRequest;
import de.lembergmax.gitmax.domain.model.UpdateRequest;
import de.lembergmax.gitmax.git.GitEngine;
import de.lembergmax.gitmax.git.GitProgress;
import de.lembergmax.gitmax.storage.MemoryKeyValueStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class OperationQueueTest {

    private static final long TIMEOUT_MS = 5_000L;

    private final FakeEngine engine = new FakeEngine();
    private final MemoryKeyValueStore store = new MemoryKeyValueStore();
    private final AtomicLong clock = new AtomicLong(1_000L);
    private ExecutorService workers;
    private OperationQueue queue;

    @Before
    public void setUp() {
        workers = Executors.newFixedThreadPool(OperationQueue.MAX_CLONES + OperationQueue.MAX_OTHERS);
        queue = newQueue(engine);
    }

    @After
    public void tearDown() {
        workers.shutdownNow();
    }

    @Test
    public void operationRunsToSuccessAndRecordsTimes() throws Exception {
        final long id = queue.enqueue(Operations.update(update("alpha")));

        awaitState(id, OperationState.SUCCEEDED);

        final OperationEntry entry = entry(id);
        assertEquals(OperationOutcome.FAST_FORWARDED, entry.outcome());
        assertEquals("alpha", entry.title());
        assertNull(entry.failure());
        assertTrue(entry.startedAt() >= entry.enqueuedAt());
        assertTrue(entry.finishedAt() >= entry.startedAt());
    }

    @Test
    public void onlyOneCloneRunsAtATime() throws Exception {
        engine.hold("a");
        engine.hold("b");
        engine.hold("c");
        final long first = queue.enqueue(Operations.clone(cloneRequest("a")));
        final long second = queue.enqueue(Operations.clone(cloneRequest("b")));
        final long third = queue.enqueue(Operations.clone(cloneRequest("c")));

        awaitState(first, OperationState.RUNNING);
        assertEquals(OperationState.QUEUED, entry(second).state());
        assertEquals(OperationState.QUEUED, entry(third).state());

        engine.open("a");
        awaitState(second, OperationState.RUNNING);
        engine.open("b");
        engine.open("c");
        awaitState(third, OperationState.SUCCEEDED);

        assertEquals(1, engine.maxRunningClones());
        assertEquals(List.of("a", "b", "c"), engine.startOrder());
    }

    @Test
    public void otherOperationsRunTwoAtATime() throws Exception {
        for (final String name : List.of("a", "b", "c", "d")) {
            engine.hold(name);
        }
        final List<Long> ids = new ArrayList<>();
        for (final String name : List.of("a", "b", "c", "d")) {
            ids.add(queue.enqueue(Operations.fetch(new File(name))));
        }

        await(() -> count(OperationState.RUNNING) == 2);
        assertEquals(2, count(OperationState.QUEUED));

        for (final String name : List.of("a", "b", "c", "d")) {
            engine.open(name);
        }
        awaitState(ids.get(3), OperationState.SUCCEEDED);
        assertEquals(2, engine.maxRunning());
    }

    @Test
    public void aCloneAndTwoOtherOperationsRunTogether() throws Exception {
        for (final String name : List.of("clone", "x", "y", "z")) {
            engine.hold(name);
        }
        queue.enqueue(Operations.clone(cloneRequest("clone")));
        queue.enqueue(Operations.fetch(new File("x")));
        queue.enqueue(Operations.fetch(new File("y")));
        final long waiting = queue.enqueue(Operations.fetch(new File("z")));

        await(() -> count(OperationState.RUNNING) == 3);
        assertEquals(OperationState.QUEUED, entry(waiting).state());

        for (final String name : List.of("clone", "x", "y", "z")) {
            engine.open(name);
        }
        awaitState(waiting, OperationState.SUCCEEDED);
    }

    @Test
    public void sameRepoFolderIsNeverUsedTwiceAtOnce() throws Exception {
        engine.hold("alpha");
        final long update = queue.enqueue(Operations.update(update("alpha")));
        final long push = queue.enqueue(Operations.push(new PushRequest(new File("alpha"), false, false)));

        awaitState(update, OperationState.RUNNING);
        assertEquals(OperationState.QUEUED, entry(push).state());

        engine.open("alpha");
        awaitState(push, OperationState.SUCCEEDED);
        assertEquals(1, engine.maxRunning());
    }

    @Test
    public void aFailureDoesNotStopTheOtherOperations() throws Exception {
        engine.failWith("broken", new GitFailureException(GitFailureKind.CONFLICT, "Konflikte in 2 Datei(en)",
                List.of("a.txt", "b.txt"), null));
        final long broken = queue.enqueue(Operations.update(update("broken")));
        final long fine = queue.enqueue(Operations.update(update("fine")));

        awaitState(broken, OperationState.FAILED);
        awaitState(fine, OperationState.SUCCEEDED);

        final OperationEntry.Failure failure = entry(broken).failure();
        assertNotNull(failure);
        assertEquals(GitFailureKind.CONFLICT, failure.kind());
        assertEquals(List.of("a.txt", "b.txt"), failure.paths());
    }

    @Test
    public void anUnexpectedExceptionBecomesAFailureInsteadOfKillingTheWorker() throws Exception {
        engine.crashWith(new IllegalStateException("kaputt"));
        final long id = queue.enqueue(Operations.fetch(new File("alpha")));

        awaitState(id, OperationState.FAILED);

        assertEquals(GitFailureKind.UNKNOWN, entry(id).failure().kind());
        engine.crashWith(null);
        final long next = queue.enqueue(Operations.fetch(new File("beta")));
        awaitState(next, OperationState.SUCCEEDED);
    }

    @Test
    public void anErrorBecomesAFailureInsteadOfLeavingTheOperationRunningForever() throws Exception {
        engine.crashWith(new OutOfMemoryError("Java heap space"));
        final long id = queue.enqueue(Operations.fetch(new File("alpha")));

        awaitState(id, OperationState.FAILED);

        assertEquals(GitFailureKind.UNKNOWN, entry(id).failure().kind());
        await(() -> !queue.hasActive());
        engine.crashWith(null);
        final long next = queue.enqueue(Operations.fetch(new File("alpha")));
        awaitState(next, OperationState.SUCCEEDED);
    }

    @Test
    public void cancellingAQueuedOperationEndsItAtOnce() throws Exception {
        engine.hold("a");
        final long running = queue.enqueue(Operations.clone(cloneRequest("a")));
        final long waiting = queue.enqueue(Operations.clone(cloneRequest("b")));
        awaitState(running, OperationState.RUNNING);

        queue.cancel(waiting);

        assertEquals(OperationState.CANCELLED, entry(waiting).state());
        engine.open("a");
        awaitState(running, OperationState.SUCCEEDED);
        assertEquals(List.of("a"), engine.startOrder());
    }

    @Test
    public void cancellingARunningOperationReachesTheEngine() throws Exception {
        engine.hold("a");
        final long id = queue.enqueue(Operations.clone(cloneRequest("a")));
        awaitState(id, OperationState.RUNNING);

        queue.cancel(id);

        awaitState(id, OperationState.CANCELLED);
        assertEquals(GitFailureKind.CANCELLED, entry(id).failure().kind());
    }

    @Test
    public void cancelAllEndsRunningAndWaitingOperations() throws Exception {
        engine.hold("a");
        engine.hold("b");
        final long first = queue.enqueue(Operations.clone(cloneRequest("a")));
        final long second = queue.enqueue(Operations.clone(cloneRequest("b")));
        awaitState(first, OperationState.RUNNING);

        queue.cancelAll();

        awaitState(first, OperationState.CANCELLED);
        assertEquals(OperationState.CANCELLED, entry(second).state());
        await(() -> !queue.hasActive());
    }

    @Test
    public void retryQueuesAFailedOperationAgain() throws Exception {
        engine.failWith("alpha", new GitFailureException(GitFailureKind.NETWORK, "offline", null));
        final long failed = queue.enqueue(Operations.fetch(new File("alpha")));
        awaitState(failed, OperationState.FAILED);
        assertTrue(queue.canRetry(failed));
        engine.clearFailure("alpha");

        final OptionalLong retried = queue.retry(failed);

        assertTrue(retried.isPresent());
        assertNotEquals(failed, retried.getAsLong());
        awaitState(retried.getAsLong(), OperationState.SUCCEEDED);
    }

    @Test
    public void restrictedNetworkFailsNewOperationsImmediately() {
        final AtomicBoolean allowed = new AtomicBoolean(false);
        final OperationQueue restricted = newQueue(engine, allowed::get);

        final long id = restricted.enqueue(Operations.fetch(new File("alpha")));

        final OperationEntry entry = restricted.snapshot().stream().filter(e -> e.id() == id).findFirst().orElseThrow();
        assertEquals(OperationState.FAILED, entry.state());
        assertEquals(GitFailureKind.METERED_NETWORK, entry.failure().kind());
        assertTrue(engine.startOrder().isEmpty());
        assertFalse(restricted.hasActive());
    }

    @Test
    public void retryAfterAMeteredFailureRunsOverMobileDataAndOpensAWindow() throws Exception {
        final AtomicBoolean allowed = new AtomicBoolean(false);
        queue = newQueue(engine, allowed::get);
        final long failed = queue.enqueue(Operations.fetch(new File("alpha")));
        assertEquals(OperationState.FAILED, entry(failed).state());

        final long retried = queue.retry(failed).orElseThrow();
        awaitState(retried, OperationState.SUCCEEDED);

        // Weitere Vorgänge im Zeitfenster laufen ohne neue Bestätigung.
        final long next = queue.enqueue(Operations.fetch(new File("beta")));
        awaitState(next, OperationState.SUCCEEDED);
    }

    @Test
    public void theMobileDataWindowExpires() throws Exception {
        final AtomicBoolean allowed = new AtomicBoolean(false);
        queue = newQueue(engine, allowed::get);
        final long failed = queue.enqueue(Operations.fetch(new File("alpha")));
        awaitState(queue.retry(failed).orElseThrow(), OperationState.SUCCEEDED);

        clock.addAndGet(OperationQueue.METERED_WINDOW_MS + 1L);
        final long later = queue.enqueue(Operations.fetch(new File("beta")));

        assertEquals(OperationState.FAILED, entry(later).state());
        assertEquals(GitFailureKind.METERED_NETWORK, entry(later).failure().kind());
    }

    @Test
    public void retryingAnOtherFailureDoesNotOpenTheMobileDataWindow() throws Exception {
        final AtomicBoolean allowed = new AtomicBoolean(true);
        queue = newQueue(engine, allowed::get);
        engine.failWith("alpha", new GitFailureException(GitFailureKind.NETWORK, "offline", null));
        final long failed = queue.enqueue(Operations.fetch(new File("alpha")));
        awaitState(failed, OperationState.FAILED);
        engine.clearFailure("alpha");
        allowed.set(false);

        final long retried = queue.retry(failed).orElseThrow();

        // Der Wiederholungsversuch scheitert jetzt an der Netz-Regel und bleibt ohne Fenster.
        assertEquals(OperationState.FAILED, entry(retried).state());
        assertEquals(GitFailureKind.METERED_NETWORK, entry(retried).failure().kind());
    }

    @Test
    public void aSucceededOperationCannotBeRetried() throws Exception {
        final long id = queue.enqueue(Operations.fetch(new File("alpha")));
        awaitState(id, OperationState.SUCCEEDED);

        assertFalse(queue.canRetry(id));
        assertFalse(queue.retry(id).isPresent());
    }

    @Test
    public void clearFinishedKeepsOnlyActiveOperations() throws Exception {
        engine.hold("slow");
        final long done = queue.enqueue(Operations.fetch(new File("fast")));
        awaitState(done, OperationState.SUCCEEDED);
        final long slow = queue.enqueue(Operations.fetch(new File("slow")));
        awaitState(slow, OperationState.RUNNING);

        queue.clearFinished();

        assertEquals(1, queue.snapshot().size());
        assertEquals(slow, queue.snapshot().get(0).id());
        engine.open("slow");
    }

    @Test
    public void listenersSeeProgressAndTheFinalState() throws Exception {
        final List<OperationEntry> seen = new CopyOnWriteArrayList<>();
        queue.addListener(entries -> seen.addAll(entries));

        final long id = queue.enqueue(Operations.fetch(new File("alpha")));
        awaitState(id, OperationState.SUCCEEDED);

        assertTrue(seen.stream().anyMatch(entry -> entry.progress() != null
                && entry.progress().phase() == GitProgress.Phase.RECEIVING));
        assertTrue(seen.stream().anyMatch(entry -> entry.state() == OperationState.SUCCEEDED));
    }

    @Test
    public void historySurvivesARestartAndInterruptedOperationsCountAsCancelled() throws Exception {
        engine.hold("alpha");
        final long running = queue.enqueue(Operations.fetch(new File("alpha")));
        final long done = queue.enqueue(Operations.fetch(new File("beta")));
        awaitState(running, OperationState.RUNNING);
        awaitState(done, OperationState.SUCCEEDED);

        final OperationQueue restarted = newQueue(new FakeEngine());

        final List<OperationEntry> entries = restarted.snapshot();
        assertEquals(2, entries.size());
        assertEquals(OperationState.CANCELLED, entries.get(0).state());
        assertEquals(OperationHistory.INTERRUPTED_MESSAGE, entries.get(0).failure().message());
        assertEquals(OperationState.SUCCEEDED, entries.get(1).state());
        assertFalse(restarted.canRetry(entries.get(0).id()));
        final long fresh = restarted.enqueue(Operations.fetch(new File("gamma")));
        assertTrue(fresh > Math.max(running, done));
        engine.open("alpha");
    }

    @Test
    public void historyKeepsOnlyTheLastFinishedOperations() throws Exception {
        long last = 0L;
        for (int index = 0; index < OperationHistory.MAX_FINISHED + 5; index += 1) {
            last = queue.enqueue(Operations.fetch(new File("repo" + index)));
        }
        awaitState(last, OperationState.SUCCEEDED);
        await(() -> !queue.hasActive());

        final List<OperationEntry> stored = new OperationHistory(store).load(clock.get());

        assertEquals(OperationHistory.MAX_FINISHED, stored.size());
        assertEquals(last, stored.get(stored.size() - 1).id());
    }

    /* ------------------------------------------------------------------------------------------ */

    private OperationQueue newQueue(
            final GitEngine forQueue
    ) {
        return new OperationQueue(forQueue, workers, clock::incrementAndGet, new OperationHistory(store));
    }

    private OperationQueue newQueue(
            final GitEngine forQueue,
            final NetworkPolicy network
    ) {
        return new OperationQueue(forQueue, workers, clock::incrementAndGet, new OperationHistory(store), network);
    }

    private static UpdateRequest update(
            final String name
    ) {
        return new UpdateRequest(new File(name), UpdateRequest.Strategy.MERGE, UpdateRequest.ConflictPolicy.ABORT, false);
    }

    private static CloneRequest cloneRequest(
            final String name
    ) {
        return new CloneRequest(RemoteUrl.parse("https://example.invalid/max/" + name + ".git").orElseThrow(),
                new File(name), null, false, false);
    }

    private OperationEntry entry(
            final long id
    ) {
        return queue.snapshot().stream().filter(entry -> entry.id() == id).findFirst().orElseThrow();
    }

    private long count(
            final OperationState state
    ) {
        return queue.snapshot().stream().filter(entry -> entry.state() == state).count();
    }

    private void awaitState(
            final long id,
            final OperationState state
    ) throws InterruptedException {
        await(() -> entry(id).state() == state);
    }

    private static void await(
            final BooleanSupplier condition
    ) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Bedingung wurde nicht innerhalb von " + TIMEOUT_MS + " ms erfüllt");
            }
            Thread.sleep(5L);
        }
    }
}
