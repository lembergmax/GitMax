package de.lembergmax.gitmax.git;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.CloneJournal;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.ChangedFile;
import de.lembergmax.gitmax.domain.model.CloneRequest;
import de.lembergmax.gitmax.domain.model.CommitRequest;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.PushRequest;
import de.lembergmax.gitmax.domain.model.UpdateOutcome;
import de.lembergmax.gitmax.domain.model.UpdateRequest;
import de.lembergmax.gitmax.domain.model.WorkingTree;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RebaseCommand;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefLeaseSpec;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * {@link GitEngine} auf Basis von JGit. Läuft ohne Android-Klassen und ist deshalb auf der JVM gegen
 * lokale Remotes testbar.
 */
public final class JgitEngine implements GitEngine {

    private static final int TIMEOUT_SECONDS = 60;
    private static final int SHORT_ID_LENGTH = 7;
    private static final String DEFAULT_REMOTE = "origin";
    private static final int MAX_SUBMODULE_DEPTH = 5;

    private final GitCredentials credentials;
    private final Predicate<File> sharedStorage;
    private final CloneJournal journal;
    private final Function<RemoteUrl, String> transportUrl;
    private final GitTransports transports;
    private final IdentityConfigurer identity;

    /**
     * @param credentials   Zugangsdaten je Host
     * @param sharedStorage sagt, ob ein Ordner auf dem Telefonspeicher liegt (dann Symlinks und Dateimodus aus)
     * @param journal       Buch über laufende Klone
     * @param identities    Commit-Identität je Repo; sie landet in der Repo-Konfiguration, damit Merges und
     *                      Rebases nicht den Android-Benutzernamen als Autor tragen
     */
    public JgitEngine(
            @NonNull final GitCredentials credentials,
            @NonNull final Predicate<File> sharedStorage,
            @NonNull final CloneJournal journal,
            @NonNull final IdentitySource identities
    ) {
        this(credentials, sharedStorage, journal, identities, RemoteUrl::toCanonicalUrl);
    }

    /**
     * Wie der andere Konstruktor, aber mit eigener Abbildung der Adresse auf das, was JGit zum Klonen öffnet
     * (Standard: die kanonische Adresse). So lässt sich eine Adresse auf einen Spiegel oder, in Tests, auf ein
     * lokales Bare-Repo umlenken.
     */
    public JgitEngine(
            @NonNull final GitCredentials credentials,
            @NonNull final Predicate<File> sharedStorage,
            @NonNull final CloneJournal journal,
            @NonNull final IdentitySource identities,
            @NonNull final Function<RemoteUrl, String> transportUrl
    ) {
        this(credentials, sharedStorage, journal, identities, transportUrl, GitTransports.NONE);
    }

    /** Alle Bausteine einzeln; die kürzeren Konstruktoren setzen Voreinstellungen. */
    public JgitEngine(
            @NonNull final GitCredentials credentials,
            @NonNull final Predicate<File> sharedStorage,
            @NonNull final CloneJournal journal,
            @NonNull final IdentitySource identities,
            @NonNull final Function<RemoteUrl, String> transportUrl,
            @NonNull final GitTransports transports
    ) {
        this.transports = Objects.requireNonNull(transports, "transports");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.sharedStorage = Objects.requireNonNull(sharedStorage, "sharedStorage");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.identity = new IdentityConfigurer(Objects.requireNonNull(identities, "identities"));
        this.transportUrl = Objects.requireNonNull(transportUrl, "transportUrl");
    }

    /** {@code true} für Pfade im Telefonspeicher ({@code /storage/…} bzw. {@code /sdcard/…}). */
    @SuppressLint("SdCardPath") // Es wird nur das Präfix eines Pfads erkannt, kein Pfad fest eingebaut.
    public static boolean isSharedStoragePath(
            @NonNull final File file
    ) {
        final String path = file.getAbsolutePath();
        return path.startsWith("/storage/") || path.startsWith("/sdcard");
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Klonen                                                                                     */
    /* ------------------------------------------------------------------------------------------ */

    @Override
    public void cloneRepository(
            @NonNull final CloneRequest request,
            @NonNull final GitProgress progress
    ) throws GitFailureException {
        final File target = request.target();
        requireEmptyTarget(target);
        journal.begin(target);
        boolean completed = false;
        try {
            doClone(request, progress);
            completed = true;
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, progress.isCancelled());
        } finally {
            // Auch bei einem Error (zu wenig Speicher) darf kein halber Klon liegen bleiben, sobald das Journal ihn vergisst.
            if (!completed) {
                removeQuietly(target);
            }
            journal.end(target);
        }
    }

    private void doClone(
            final CloneRequest request,
            final GitProgress progress
    ) throws GitAPIException, IOException, GitFailureException {
        final ProgressAdapter monitor = new ProgressAdapter(progress);
        final CredentialsProvider provider = credentials.forUrl(request.url());
        final TransportConfigCallback callback = transports.forUrl(request.url());
        final CloneCommand command = Git.cloneRepository()
                .setURI(transportUrl.apply(request.url()))
                .setDirectory(request.target())
                .setNoCheckout(true)
                .setCredentialsProvider(provider)
                .setTransportConfigCallback(callback)
                .setProgressMonitor(monitor)
                .setTimeout(TIMEOUT_SECONDS);
        if (request.branch() != null) {
            command.setBranch(Constants.R_HEADS + request.branch());
        }
        if (request.shallow()) {
            command.setDepth(1);
        }
        try (Git git = command.call()) {
            if (sharedStorage.test(request.target())) {
                final StoredConfig config = git.getRepository().getConfig();
                RepoConfigurator.applySharedStorageFlags(config);
                config.save();
            }
            identity.ensure(git.getRepository());
            checkoutHead(git, monitor);
            if (request.submodules()) {
                updateSubmodules(git, provider, callback, monitor, 0);
            }
        }
    }

    private static void checkoutHead(
            final Git git,
            final ProgressAdapter monitor
    ) throws GitAPIException, IOException {
        if (git.getRepository().resolve(Constants.HEAD) == null) {
            return;
        }
        git.reset()
                .setMode(ResetCommand.ResetType.HARD)
                .setRef(Constants.HEAD)
                .setProgressMonitor(monitor)
                .call();
    }

    private static void updateSubmodules(
            final Git git,
            @Nullable final CredentialsProvider provider,
            @Nullable final TransportConfigCallback callback,
            final ProgressAdapter monitor,
            final int depth
    ) throws GitAPIException, IOException {
        if (depth >= MAX_SUBMODULE_DEPTH) {
            return;
        }
        git.submoduleInit().call();
        final Collection<String> updated = git.submoduleUpdate()
                .setCredentialsProvider(provider)
                .setTransportConfigCallback(callback)
                .setProgressMonitor(monitor)
                .call();
        for (final String path : updated) {
            final File submodule = new File(git.getRepository().getWorkTree(), path);
            if (new File(submodule, ".git").exists()) {
                try (Git inner = Git.open(submodule)) {
                    updateSubmodules(inner, provider, callback, monitor, depth + 1);
                }
            }
        }
    }

    @Override
    public void updateSubmodules(
            @NonNull final File repo,
            @NonNull final GitProgress progress
    ) throws GitFailureException {
        try (Git git = open(repo)) {
            final ProgressAdapter monitor = new ProgressAdapter(progress);
            monitor.beginTask("Submodule", ProgressMonitor.UNKNOWN);
            final String remote = remoteOf(git.getRepository());
            updateSubmodules(git, credentialsFor(git.getRepository(), remote), transportFor(git.getRepository(), remote), monitor, 0);
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, progress.isCancelled());
        }
    }

    private static void requireEmptyTarget(
            final File target
    ) throws GitFailureException {
        if (!target.exists()) {
            return;
        }
        final String[] content = target.list();
        if (!target.isDirectory() || content == null || content.length > 0) {
            throw new GitFailureException(GitFailureKind.ALREADY_EXISTS,
                    "The target folder already exists and is not empty: " + target.getAbsolutePath(), null);
        }
    }

    private static void removeQuietly(
            final File target
    ) {
        try {
            FileUtils.delete(target, FileUtils.RECURSIVE | FileUtils.SKIP_MISSING | FileUtils.IGNORE_ERRORS);
        } catch (final IOException cannotDelete) {
            // Der Ordner bleibt liegen; das Klon-Journal räumt ihn beim nächsten Start auf.
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Abrufen und Aktualisieren                                                                  */
    /* ------------------------------------------------------------------------------------------ */

    @Override
    public void fetch(
            @NonNull final File repo,
            @NonNull final GitProgress progress
    ) throws GitFailureException {
        try (Git git = open(repo)) {
            fetchRemote(git, remoteOf(git.getRepository()), progress);
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, progress.isCancelled());
        }
    }

    @NonNull
    @Override
    public UpdateOutcome update(
            @NonNull final UpdateRequest request,
            @NonNull final GitProgress progress
    ) throws GitFailureException {
        try (Git git = open(request.repo())) {
            return doUpdate(git, request, progress);
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, progress.isCancelled());
        }
    }

    private UpdateOutcome doUpdate(
            final Git git,
            final UpdateRequest request,
            final GitProgress progress
    ) throws GitAPIException, IOException, GitFailureException {
        final Repository repository = git.getRepository();
        if (repository.getRepositoryState() != RepositoryState.SAFE) {
            throw new GitFailureException(GitFailureKind.CONFLICT, "A merge or rebase is still in progress in the repo", null);
        }
        final Ref head = repository.exactRef(Constants.HEAD);
        if (head == null || !head.isSymbolic()) {
            return UpdateOutcome.SKIPPED_DETACHED;
        }
        final String branch = repository.getBranch();
        final BranchConfig branchConfig = new BranchConfig(repository.getConfig(), branch);
        final String trackingRef = branchConfig.getRemoteTrackingBranch();
        if (trackingRef == null) {
            return UpdateOutcome.SKIPPED_NO_UPSTREAM;
        }
        fetchRemote(git, branchConfig.getRemote(), progress);

        final BranchTrackingStatus tracking = BranchTrackingStatus.of(repository, branch);
        if (tracking == null || tracking.getBehindCount() == 0) {
            return UpdateOutcome.UP_TO_DATE;
        }
        progress.onProgress(GitProgress.Phase.MERGING, GitProgress.UNKNOWN);
        identity.ensure(repository);
        return integrate(git, request, tracking, trackingRef);
    }

    private UpdateOutcome integrate(
            final Git git,
            final UpdateRequest request,
            final BranchTrackingStatus tracking,
            final String trackingRef
    ) throws GitAPIException, IOException, GitFailureException {
        RevCommit stash = null;
        if (request.autoStash() && git.status().call().hasUncommittedChanges()) {
            stash = git.stashCreate().call();
        }
        boolean keepStash = false;
        try {
            return mergeOrRebase(git, request, tracking, trackingRef);
        } catch (final GitFailureException failure) {
            // Bleibt das Repo mitten im Konflikt stehen, gehört die Wegstellung dem Nutzer.
            keepStash = git.getRepository().getRepositoryState() != RepositoryState.SAFE;
            throw failure;
        } finally {
            if (stash != null && !keepStash) {
                restoreStash(git, stash);
            }
        }
    }

    private UpdateOutcome mergeOrRebase(
            final Git git,
            final UpdateRequest request,
            final BranchTrackingStatus tracking,
            final String trackingRef
    ) throws GitAPIException, IOException, GitFailureException {
        final ObjectId target = git.getRepository().resolve(trackingRef);
        if (tracking.getAheadCount() == 0) {
            return fastForward(git, target);
        }
        switch (request.strategy()) {
            case FAST_FORWARD_ONLY:
                throw new GitFailureException(GitFailureKind.NOT_FAST_FORWARD,
                        "Local and remote have diverged; a fast-forward is not possible", null);
            case REBASE:
                return rebase(git, request, trackingRef);
            case MERGE:
            default:
                return merge(git, request, target, trackingRef);
        }
    }

    private static UpdateOutcome fastForward(
            final Git git,
            final ObjectId target
    ) throws GitAPIException, GitFailureException {
        final MergeResult result = git.merge()
                .include(target)
                .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
                .call();
        if (result.getMergeStatus().isSuccessful()) {
            return UpdateOutcome.FAST_FORWARDED;
        }
        throw mergeFailure(result);
    }

    private UpdateOutcome merge(
            final Git git,
            final UpdateRequest request,
            final ObjectId target,
            final String trackingRef
    ) throws GitAPIException, IOException, GitFailureException {
        // Ein Konflikt wird mit "reset --hard" zurückgenommen; das risse auch Änderungen mit, die mit dem Merge nichts zu tun
        // haben. Darum beginnt ein echter Merge nur bei sauberem Arbeitsbaum (oder nach dem Wegstellen mit autoStash).
        if (git.status().call().hasUncommittedChanges()) {
            throw new GitFailureException(GitFailureKind.DIRTY_TREE,
                    "Local changes prevent the merge", null);
        }
        final MergeResult result = git.merge()
                .include(target)
                .setFastForward(MergeCommand.FastForwardMode.FF)
                .setCommit(true)
                .setMessage("Merge remote-tracking branch '" + Repository.shortenRefName(trackingRef) + "'")
                .call();
        if (result.getMergeStatus() == MergeResult.MergeStatus.MERGED
                || result.getMergeStatus() == MergeResult.MergeStatus.FAST_FORWARD) {
            return UpdateOutcome.MERGED;
        }
        if (result.getMergeStatus() == MergeResult.MergeStatus.CONFLICTING) {
            final List<String> paths = new ArrayList<>(new TreeSet<>(result.getConflicts().keySet()));
            if (request.onConflict() == UpdateRequest.ConflictPolicy.ABORT) {
                resetMergeState(git);
            }
            throw new GitFailureException(GitFailureKind.CONFLICT,
                    "Conflicts in " + paths.size() + " file(s)", paths, null);
        }
        throw mergeFailure(result);
    }

    private UpdateOutcome rebase(
            final Git git,
            final UpdateRequest request,
            final String trackingRef
    ) throws GitAPIException, GitFailureException {
        final RebaseResult result = git.rebase().setUpstream(trackingRef).call();
        switch (result.getStatus()) {
            case OK:
            case UP_TO_DATE:
            case FAST_FORWARD:
                return UpdateOutcome.REBASED;
            case UNCOMMITTED_CHANGES:
                throw new GitFailureException(GitFailureKind.DIRTY_TREE,
                        "Local changes prevent the rebase", null);
            case CONFLICTS:
            case STOPPED:
                final List<String> paths = result.getConflicts() == null ? List.of() : new ArrayList<>(result.getConflicts());
                if (request.onConflict() == UpdateRequest.ConflictPolicy.ABORT) {
                    git.rebase().setOperation(RebaseCommand.Operation.ABORT).call();
                }
                throw new GitFailureException(GitFailureKind.CONFLICT,
                        "Conflicts while rebasing in " + paths.size() + " file(s)", paths, null);
            default:
                throw new GitFailureException(GitFailureKind.UNKNOWN, "Rebase not possible: " + result.getStatus(), null);
        }
    }

    private static GitFailureException mergeFailure(
            final MergeResult result
    ) {
        final Set<String> paths = new TreeSet<>();
        if (result.getCheckoutConflicts() != null) {
            paths.addAll(result.getCheckoutConflicts());
        }
        if (result.getFailingPaths() != null) {
            paths.addAll(result.getFailingPaths().keySet());
        }
        if (result.getMergeStatus() == MergeResult.MergeStatus.CHECKOUT_CONFLICT
                || result.getMergeStatus() == MergeResult.MergeStatus.FAILED) {
            return new GitFailureException(GitFailureKind.DIRTY_TREE,
                    "Local changes would be overwritten", new ArrayList<>(paths), null);
        }
        return new GitFailureException(GitFailureKind.UNKNOWN, "Merge not possible: " + result.getMergeStatus(), null);
    }

    private static void restoreStash(
            final Git git,
            final RevCommit stash
    ) throws GitAPIException, GitFailureException {
        try {
            git.stashApply().setStashRef(stash.getName()).call();
            git.stashDrop().setStashRef(0).call();
        } catch (final GitAPIException failure) {
            throw new GitFailureException(GitFailureKind.CONFLICT,
                    "The stashed changes could not be restored; they remain in the stash", failure);
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Status, Stagen, Commit                                                                     */
    /* ------------------------------------------------------------------------------------------ */

    @NonNull
    @Override
    public WorkingTree status(
            @NonNull final File repo
    ) throws GitFailureException {
        try (Git git = open(repo)) {
            final Status status = git.status().call();
            return new WorkingTree(
                    files(status.getConflicting(), ChangedFile.Kind.CONFLICT),
                    merge(files(status.getAdded(), ChangedFile.Kind.ADDED),
                            files(status.getChanged(), ChangedFile.Kind.MODIFIED),
                            files(status.getRemoved(), ChangedFile.Kind.DELETED)),
                    merge(files(status.getModified(), ChangedFile.Kind.MODIFIED),
                            files(status.getMissing(), ChangedFile.Kind.DELETED)),
                    files(status.getUntracked(), ChangedFile.Kind.UNTRACKED),
                    git.getRepository().getRepositoryState() != RepositoryState.SAFE
            );
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    @Override
    public void stage(
            @NonNull final File repo,
            @NonNull final Collection<String> paths
    ) throws GitFailureException {
        if (paths.isEmpty()) {
            return;
        }
        try (Git git = open(repo)) {
            final var add = git.add();
            final var remove = git.rm().setCached(true);
            boolean anyAdd = false;
            boolean anyRemove = false;
            for (final String path : paths) {
                if (new File(repo, path).exists()) {
                    add.addFilepattern(path);
                    anyAdd = true;
                } else {
                    remove.addFilepattern(path);
                    anyRemove = true;
                }
            }
            if (anyAdd) {
                add.call();
            }
            if (anyRemove) {
                remove.call();
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    @Override
    public void unstage(
            @NonNull final File repo,
            @NonNull final Collection<String> paths
    ) throws GitFailureException {
        if (paths.isEmpty()) {
            return;
        }
        try (Git git = open(repo)) {
            if (git.getRepository().resolve(Constants.HEAD) == null) {
                final var remove = git.rm().setCached(true);
                paths.forEach(remove::addFilepattern);
                remove.call();
                return;
            }
            final ResetCommand reset = git.reset().setRef(Constants.HEAD);
            paths.forEach(reset::addPath);
            reset.call();
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    @Override
    public void discard(
            @NonNull final File repo,
            @NonNull final Collection<String> paths
    ) throws GitFailureException {
        if (paths.isEmpty()) {
            return;
        }
        try (Git git = open(repo)) {
            final Status status = git.status().call();
            final List<String> restore = new ArrayList<>();
            final List<String> delete = new ArrayList<>();
            for (final String path : paths) {
                if (status.getUntracked().contains(path)) {
                    delete.add(path);
                } else if (status.getAdded().contains(path)) {
                    delete.add(path);
                    restore.add(path);
                } else {
                    restore.add(path);
                }
            }
            if (!restore.isEmpty()) {
                unstage(repo, restore);
            }
            for (final String path : delete) {
                FileUtils.delete(new File(repo, path), FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
            }
            final List<String> tracked = new ArrayList<>(restore);
            tracked.removeAll(delete);
            if (!tracked.isEmpty()) {
                final var checkout = git.checkout();
                tracked.forEach(checkout::addPath);
                checkout.call();
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    @NonNull
    @Override
    public String commit(
            @NonNull final CommitRequest request
    ) throws GitFailureException {
        if (request.message().isBlank()) {
            throw new IllegalArgumentException("The commit message must not be empty");
        }
        try (Git git = open(request.repo())) {
            final Set<String> conflicts = git.status().call().getConflicting();
            if (!conflicts.isEmpty()) {
                throw new GitFailureException(GitFailureKind.CONFLICT,
                        "There are unresolved conflicts", new ArrayList<>(new TreeSet<>(conflicts)), null);
            }
            final PersonIdent author = new PersonIdent(request.identity().name(), request.identity().email());
            final RevCommit commit = git.commit()
                    .setMessage(request.message().strip())
                    .setAuthor(author)
                    .setCommitter(author)
                    .setAmend(request.amend())
                    .setAllowEmpty(request.amend())
                    .call();
            return commit.abbreviate(SHORT_ID_LENGTH).name();
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Push                                                                                       */
    /* ------------------------------------------------------------------------------------------ */

    @Override
    public void push(
            @NonNull final PushRequest request,
            @NonNull final GitProgress progress
    ) throws GitFailureException {
        try (Git git = open(request.repo())) {
            doPush(git, request, progress);
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, progress.isCancelled());
        }
    }

    private void doPush(
            final Git git,
            final PushRequest request,
            final GitProgress progress
    ) throws GitAPIException, IOException, GitFailureException {
        final Repository repository = git.getRepository();
        final Ref head = repository.exactRef(Constants.HEAD);
        if (head == null || !head.isSymbolic()) {
            throw new GitFailureException(GitFailureKind.NO_UPSTREAM, "HEAD is detached; there is no branch to push", null);
        }
        final String branch = repository.getBranch();
        final BranchConfig branchConfig = new BranchConfig(repository.getConfig(), branch);
        final boolean hasUpstream = branchConfig.getRemoteTrackingBranch() != null;
        final String remote = hasUpstream ? branchConfig.getRemote() : remoteOf(repository);

        // Vorab abrufen: steht das Remote vor dem lokalen Stand, ist Pushen der falsche Weg.
        fetchRemote(git, remote, progress);
        final BranchTrackingStatus tracking = BranchTrackingStatus.of(repository, branch);
        if (!request.force() && tracking != null && tracking.getBehindCount() > 0) {
            throw new GitFailureException(GitFailureKind.NOT_FAST_FORWARD,
                    "The remote has " + tracking.getBehindCount() + " new commit(s); update first", null);
        }

        final String refName = Constants.R_HEADS + branch;
        final PushCommand command = git.push()
                .setRemote(remote)
                .setRefSpecs(new RefSpec(refName + ":" + refName))
                .setForce(request.force())
                .setCredentialsProvider(credentialsFor(repository, remote))
                .setTransportConfigCallback(transportFor(repository, remote))
                .setProgressMonitor(new ProgressAdapter(progress))
                .setTimeout(TIMEOUT_SECONDS);
        if (request.tags()) {
            command.setPushTags();
        }
        if (request.force() && tracking != null) {
            // Mit Absicherung: nur überschreiben, wenn das Remote noch dem zuletzt abgerufenen Stand entspricht.
            command.setRefLeaseSpecs(new RefLeaseSpec(refName, branchConfig.getRemoteTrackingBranch()));
        }

        for (final PushResult result : command.call()) {
            checkPushResult(result);
        }
        if (!hasUpstream) {
            setUpstream(repository, branch, remote);
        }
    }

    @Override
    public void pushRef(
            @NonNull final File repo,
            @NonNull final String remote,
            @NonNull final String refSpec,
            @NonNull final GitProgress progress
    ) throws GitFailureException {
        try (Git git = open(repo)) {
            final PushCommand command = git.push()
                    .setRemote(remote)
                    .setRefSpecs(new RefSpec(refSpec))
                    .setCredentialsProvider(credentialsFor(git.getRepository(), remote))
                    .setTransportConfigCallback(transportFor(git.getRepository(), remote))
                    .setProgressMonitor(new ProgressAdapter(progress))
                    .setTimeout(TIMEOUT_SECONDS);
            for (final PushResult result : command.call()) {
                checkPushResult(result);
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, progress.isCancelled());
        }
    }

    private static void checkPushResult(
            final PushResult result
    ) throws GitFailureException {
        for (final RemoteRefUpdate update : result.getRemoteUpdates()) {
            switch (update.getStatus()) {
                case OK:
                case UP_TO_DATE:
                    break;
                case REJECTED_NONFASTFORWARD:
                case REJECTED_REMOTE_CHANGED:
                    throw new GitFailureException(GitFailureKind.NOT_FAST_FORWARD,
                            "The remote rejected the change: newer commits exist", null);
                case REJECTED_OTHER_REASON:
                case REJECTED_NODELETE:
                    throw new GitFailureException(GitFailureKind.REJECTED,
                            update.getMessage() == null ? "The remote rejected the push" : update.getMessage(), null);
                default:
                    throw new GitFailureException(GitFailureKind.UNKNOWN,
                            "Push not completed: " + update.getStatus()
                                    + (update.getMessage() == null ? "" : " (" + update.getMessage() + ")"), null);
            }
        }
    }

    private static void setUpstream(
            final Repository repository,
            final String branch,
            final String remote
    ) throws IOException {
        final StoredConfig config = repository.getConfig();
        config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_REMOTE, remote);
        config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + branch);
        config.save();
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Neues Repo                                                                                 */
    /* ------------------------------------------------------------------------------------------ */

    @Override
    public void initRepository(
            @NonNull final File repo,
            @NonNull final String branch,
            @Nullable final String originUrl
    ) throws GitFailureException {
        requireEmptyTarget(repo);
        final String clean = originUrl == null ? null : RemoteOperations.cleanUrl(originUrl);
        try (Git git = Git.init().setDirectory(repo).setInitialBranch(branch).call()) {
            final StoredConfig config = git.getRepository().getConfig();
            if (sharedStorage.test(repo)) {
                RepoConfigurator.applySharedStorageFlags(config);
                config.save();
            }
            if (clean != null) {
                git.remoteAdd().setName(DEFAULT_REMOTE).setUri(new URIish(clean)).call();
            }
            identity.ensure(git.getRepository());
        } catch (final Exception failure) {
            removeQuietly(repo);
            throw GitErrorMapper.map(failure, false);
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Merge abbrechen                                                                            */
    /* ------------------------------------------------------------------------------------------ */

    @Override
    public void abortMerge(
            @NonNull final File repo
    ) throws GitFailureException {
        try (Git git = open(repo)) {
            if (git.getRepository().getRepositoryState().isRebasing()) {
                git.rebase().setOperation(RebaseCommand.Operation.ABORT).call();
            } else {
                resetMergeState(git);
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    private static void resetMergeState(
            final Git git
    ) throws GitAPIException, IOException {
        final Repository repository = git.getRepository();
        repository.writeMergeHeads(null);
        repository.writeMergeCommitMsg(null);
        repository.writeCherryPickHead(null);
        repository.writeRevertHead(null);
        if (repository.resolve(Constants.HEAD) != null) {
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(Constants.HEAD).call();
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Hilfen                                                                                     */
    /* ------------------------------------------------------------------------------------------ */

    static Git open(
            final File repo
    ) throws GitFailureException {
        try {
            return Git.open(repo);
        } catch (final IOException notARepo) {
            throw new GitFailureException(GitFailureKind.NOT_A_REPO,
                    "Not a Git repo: " + repo.getAbsolutePath(), notARepo);
        }
    }

    private void fetchRemote(
            final Git git,
            final String remote,
            final GitProgress progress
    ) throws GitAPIException, GitFailureException {
        git.fetch()
                .setRemote(remote)
                .setRemoveDeletedRefs(git.getRepository().getConfig().getBoolean("fetch", null, "prune", true))
                .setTimeout(TIMEOUT_SECONDS)
                .setProgressMonitor(new ProgressAdapter(progress))
                .setCredentialsProvider(credentialsFor(git.getRepository(), remote))
                .setTransportConfigCallback(transportFor(git.getRepository(), remote))
                .call();
    }

    @Nullable
    private CredentialsProvider credentialsFor(
            final Repository repository,
            final String remote
    ) throws GitFailureException {
        final String url = repository.getConfig().getString(
                ConfigConstants.CONFIG_REMOTE_SECTION, remote, ConfigConstants.CONFIG_KEY_URL);
        final Optional<RemoteUrl> parsed = RemoteUrl.parse(url);
        return parsed.isEmpty() ? null : credentials.forUrl(parsed.get());
    }

    @Nullable
    private TransportConfigCallback transportFor(
            final Repository repository,
            final String remote
    ) throws GitFailureException {
        final String url = repository.getConfig().getString(
                ConfigConstants.CONFIG_REMOTE_SECTION, remote, ConfigConstants.CONFIG_KEY_URL);
        final Optional<RemoteUrl> parsed = RemoteUrl.parse(url);
        return parsed.isEmpty() ? null : transports.forUrl(parsed.get());
    }

    /** Das Remote des Repos: {@code origin}, sonst das erste vorhandene. */
    private static String remoteOf(
            final Repository repository
    ) throws GitFailureException {
        final Set<String> remotes = repository.getRemoteNames();
        if (remotes.contains(DEFAULT_REMOTE)) {
            return DEFAULT_REMOTE;
        }
        return remotes.stream().findFirst().orElseThrow(() ->
                new GitFailureException(GitFailureKind.NO_UPSTREAM, "The repo has no remote", null));
    }

    private static List<ChangedFile> files(
            final Set<String> paths,
            final ChangedFile.Kind kind
    ) {
        final List<ChangedFile> files = new ArrayList<>();
        for (final String path : paths) {
            files.add(new ChangedFile(path, kind));
        }
        files.sort(Comparator.comparing(ChangedFile::path));
        return files;
    }

    @SafeVarargs
    private static List<ChangedFile> merge(
            final List<ChangedFile>... groups
    ) {
        final List<ChangedFile> merged = new ArrayList<>();
        for (final List<ChangedFile> group : groups) {
            merged.addAll(group);
        }
        merged.sort(Comparator.comparing(ChangedFile::path));
        return merged;
    }
}
