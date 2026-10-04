package de.lembergmax.gitmax.ops;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.CloneRequest;
import de.lembergmax.gitmax.domain.model.CommitRequest;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.PushRequest;
import de.lembergmax.gitmax.domain.model.UpdateOutcome;
import de.lembergmax.gitmax.domain.model.UpdateRequest;
import de.lembergmax.gitmax.domain.model.WorkingTree;
import de.lembergmax.gitmax.git.GitEngine;
import de.lembergmax.gitmax.git.GitProgress;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link GitEngine} für Warteschlangen-Tests. Jeder Vorgang hält an seinem Tor (nach Ordnername) an, bis
 * der Test es öffnet; Fehler lassen sich je Ordner vorgeben. Zählt, wie viele Vorgänge gleichzeitig laufen.
 */
final class FakeEngine implements GitEngine {

    private final Map<String, CountDownLatch> gates = new ConcurrentHashMap<>();
    private final Map<String, GitFailureException> failures = new ConcurrentHashMap<>();
    private final List<String> startOrder = new CopyOnWriteArrayList<>();
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger runningClones = new AtomicInteger();
    private final AtomicInteger maxRunning = new AtomicInteger();
    private final AtomicInteger maxRunningClones = new AtomicInteger();
    private volatile Throwable crash;

    /** Der Vorgang für diesen Ordner wartet, bis {@link #open(String)} aufgerufen wird. */
    void hold(
            final String name
    ) {
        gates.put(name, new CountDownLatch(1));
    }

    void open(
            final String name
    ) {
        final CountDownLatch gate = gates.get(name);
        if (gate != null) {
            gate.countDown();
        }
    }

    void failWith(
            final String name,
            final GitFailureException failure
    ) {
        failures.put(name, failure);
    }

    void clearFailure(
            final String name
    ) {
        failures.remove(name);
    }

    void crashWith(
            final Throwable exception
    ) {
        crash = exception;
    }

    List<String> startOrder() {
        return startOrder;
    }

    int maxRunning() {
        return maxRunning.get();
    }

    int maxRunningClones() {
        return maxRunningClones.get();
    }

    @Override
    public void cloneRepository(
            final CloneRequest request,
            final GitProgress progress
    ) throws GitFailureException {
        runningClones.incrementAndGet();
        maxRunningClones.accumulateAndGet(runningClones.get(), Math::max);
        try {
            work(request.target().getName(), progress);
        } finally {
            runningClones.decrementAndGet();
        }
    }

    @Override
    public UpdateOutcome update(
            final UpdateRequest request,
            final GitProgress progress
    ) throws GitFailureException {
        work(request.repo().getName(), progress);
        return UpdateOutcome.FAST_FORWARDED;
    }

    @Override
    public void fetch(
            final File repo,
            final GitProgress progress
    ) throws GitFailureException {
        work(repo.getName(), progress);
    }

    @Override
    public void push(
            final PushRequest request,
            final GitProgress progress
    ) throws GitFailureException {
        work(request.repo().getName(), progress);
    }

    private void work(
            final String name,
            final GitProgress progress
    ) throws GitFailureException {
        startOrder.add(name);
        maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
        try {
            progress.onProgress(GitProgress.Phase.RECEIVING, 0.5f);
            awaitGate(name, progress);
            if (crash instanceof Error error) {
                throw error;
            }
            if (crash instanceof RuntimeException exception) {
                throw exception;
            }
            final GitFailureException failure = failures.get(name);
            if (failure != null) {
                throw failure;
            }
        } finally {
            running.decrementAndGet();
        }
    }

    private void awaitGate(
            final String name,
            final GitProgress progress
    ) throws GitFailureException {
        final CountDownLatch gate = gates.get(name);
        if (gate == null) {
            return;
        }
        try {
            while (!gate.await(10, TimeUnit.MILLISECONDS)) {
                if (progress.isCancelled()) {
                    throw new GitFailureException(GitFailureKind.CANCELLED, "Abgebrochen", null);
                }
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GitFailureException(GitFailureKind.CANCELLED, "Unterbrochen", interrupted);
        }
    }

    @Override
    public void pushRef(
            final File repo,
            final String remote,
            final String refSpec,
            final GitProgress progress
    ) throws GitFailureException {
        work(repo.getName(), progress);
    }

    @Override
    public void updateSubmodules(
            final File repo,
            final GitProgress progress
    ) throws GitFailureException {
        work(repo.getName(), progress);
    }

    @Override
    public WorkingTree status(
            final File repo
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stage(
            final File repo,
            final Collection<String> paths
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unstage(
            final File repo,
            final Collection<String> paths
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void discard(
            final File repo,
            final Collection<String> paths
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String commit(
            final CommitRequest request
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void initRepository(
            final File repo,
            final String branch,
            final String originUrl
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void abortMerge(
            final File repo
    ) {
        throw new UnsupportedOperationException();
    }
}
