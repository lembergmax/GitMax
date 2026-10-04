package de.lembergmax.gitmax;

import android.content.Context;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.git.AccountCredentials;
import de.lembergmax.gitmax.git.GitAdvanced;
import de.lembergmax.gitmax.git.GitEngine;
import de.lembergmax.gitmax.git.JgitAdvanced;
import de.lembergmax.gitmax.git.JgitEngine;
import de.lembergmax.gitmax.git.RepoStatusReader;
import de.lembergmax.gitmax.git.ssh.KnownHostsStore;
import de.lembergmax.gitmax.git.ssh.SshKeyStore;
import de.lembergmax.gitmax.git.ssh.SshTransports;
import de.lembergmax.gitmax.ops.OperationHistory;
import de.lembergmax.gitmax.ops.AndroidNetworkPolicy;
import de.lembergmax.gitmax.ops.OperationQueue;
import de.lembergmax.gitmax.ops.OperationRepository;
import de.lembergmax.gitmax.provider.ProviderClients;
import de.lembergmax.gitmax.repository.AccountRepository;
import de.lembergmax.gitmax.repository.IdentityResolver;
import de.lembergmax.gitmax.repository.LocalRepoRepository;
import de.lembergmax.gitmax.repository.RemoteRepoRepository;
import de.lembergmax.gitmax.repository.RepoRemover;
import de.lembergmax.gitmax.repository.RepoCreator;
import de.lembergmax.gitmax.storage.JsonFileStore;
import de.lembergmax.gitmax.storage.KeystoreSecretCipher;
import de.lembergmax.gitmax.storage.PersistentCloneJournal;
import de.lembergmax.gitmax.storage.RepoSettingsStore;
import de.lembergmax.gitmax.storage.SecretVault;
import de.lembergmax.gitmax.storage.AppSettings;
import de.lembergmax.gitmax.storage.SharedPreferencesKeyValueStore;
import de.lembergmax.gitmax.storage.WorkspaceStore;

import java.io.File;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hält die gemeinsam genutzten Bausteine der App und reicht sie über den Konstruktor weiter. Bewusst
 * kein DI-Framework: ein paar Objekte, einmal beim Start gebaut.
 */
public final class ServiceLocator {

    private static final String STATE_FILE = "gitmax_state";
    private static final String SECRETS_FILE = "gitmax_secrets";
    private static final String CACHE_DIRECTORY = "cache";
    private static final String SSH_HOME = "ssh-home";

    private final ExecutorService io;
    private final AccountRepository accounts;
    private final RemoteRepoRepository remoteRepos;
    private final WorkspaceStore workspace;
    private final LocalRepoRepository localRepos;
    private final PersistentCloneJournal cloneJournal;
    private final GitEngine engine;
    private final GitAdvanced advanced;
    private final OperationRepository operations;
    private final RepoSettingsStore repoSettings;
    private final AppSettings settings;
    private final RepoCreator repoCreator;
    private final RepoRemover repoRemover;
    private final SshKeyStore sshKeys;
    private final KnownHostsStore knownHosts;
    private final IdentityResolver identities;

    ServiceLocator(
            @NonNull final Context context
    ) {
        Objects.requireNonNull(context, "context");
        final Context app = context.getApplicationContext();
        this.io = Executors.newFixedThreadPool(3, runnable -> {
            final Thread thread = new Thread(runnable, "gitmax-io");
            thread.setDaemon(true);
            return thread;
        });
        final SecretVault vault = new SecretVault(
                new SharedPreferencesKeyValueStore(app, SECRETS_FILE),
                new KeystoreSecretCipher()
        );
        this.accounts = new AccountRepository(
                new SharedPreferencesKeyValueStore(app, STATE_FILE),
                vault,
                ProviderClients::forProvider,
                () -> UUID.randomUUID().toString()
        );
        this.remoteRepos = new RemoteRepoRepository(
                new JsonFileStore(new File(app.getFilesDir(), CACHE_DIRECTORY)),
                accounts,
                ProviderClients::forProvider,
                System::currentTimeMillis
        );
        this.workspace = new WorkspaceStore(new SharedPreferencesKeyValueStore(app, STATE_FILE));
        this.localRepos = new LocalRepoRepository(workspace, new RepoStatusReader());

        this.cloneJournal = new PersistentCloneJournal(new SharedPreferencesKeyValueStore(app, STATE_FILE));
        this.repoSettings = new RepoSettingsStore(new SharedPreferencesKeyValueStore(app, STATE_FILE));
        this.settings = new AppSettings(new SharedPreferencesKeyValueStore(app, STATE_FILE));
        this.identities = new IdentityResolver(accounts, repoSettings);
        this.repoCreator = new RepoCreator(accounts, ProviderClients::forProvider);
        this.repoRemover = new RepoRemover(workspace, repoSettings);
        this.sshKeys = new SshKeyStore(new SharedPreferencesKeyValueStore(app, STATE_FILE), vault,
                System::currentTimeMillis, () -> UUID.randomUUID().toString());
        this.knownHosts = new KnownHostsStore(new SharedPreferencesKeyValueStore(app, STATE_FILE), System::currentTimeMillis);
        final SshTransports transports = new SshTransports(new File(app.getFilesDir(), SSH_HOME), sshKeys, knownHosts);
        this.engine = new JgitEngine(new AccountCredentials(accounts), JgitEngine::isSharedStoragePath, cloneJournal,
                identities::resolve, RemoteUrl::toCanonicalUrl, transports);
        this.advanced = new JgitAdvanced(identities::resolve);
        // Ein Klon, dazu zwei andere Vorgänge: so viele Threads braucht die Warteschlange gleichzeitig.
        final ExecutorService workers = Executors.newFixedThreadPool(
                OperationQueue.MAX_CLONES + OperationQueue.MAX_OTHERS, runnable -> {
                    final Thread thread = new Thread(runnable, "gitmax-git");
                    thread.setDaemon(true);
                    return thread;
                });
        final OperationQueue queue = new OperationQueue(
                engine,
                workers,
                System::currentTimeMillis,
                new OperationHistory(new SharedPreferencesKeyValueStore(app, STATE_FILE)),
                new AndroidNetworkPolicy(app, settings)
        );
        this.operations = new OperationRepository(app, queue, io);
    }

    /**
     * Löscht Ordner halb fertiger Klone, die ein Prozess-Tod hinterlassen hat. Blockiert; gehört auf einen
     * Hintergrund-Thread und vor den ersten neuen Vorgang.
     */
    void cleanUpInterruptedClones() {
        cloneJournal.cleanUpInterrupted();
    }

    /** Der {@code ServiceLocator} der laufenden App. */
    @NonNull
    public static ServiceLocator from(
            @NonNull final Context context
    ) {
        return ((GitMaxApplication) context.getApplicationContext()).services();
    }

    /** Hintergrund-Threads für blockierende Arbeit (Netzwerk, Dateien, Git). */
    @NonNull
    public ExecutorService io() {
        return io;
    }

    @NonNull
    public AccountRepository accounts() {
        return accounts;
    }

    @NonNull
    public RemoteRepoRepository remoteRepos() {
        return remoteRepos;
    }

    @NonNull
    public WorkspaceStore workspace() {
        return workspace;
    }

    @NonNull
    public LocalRepoRepository localRepos() {
        return localRepos;
    }

    /** Die Git-Engine für kurze, direkte Aufrufe (Status, Stagen, Commit); lange Vorgänge gehen über {@link #operations()}. */
    @NonNull
    public GitEngine engine() {
        return engine;
    }

    /** Verlauf, Diff, Branches, Stash, Tags und Remotes. */
    @NonNull
    public GitAdvanced advanced() {
        return advanced;
    }

    /** Klonen, Aktualisieren, Abrufen und Pushen als Vorgänge mit Warteschlange. */
    @NonNull
    public OperationRepository operations() {
        return operations;
    }

    /** Bestimmt die Commit-Identität je Repo. */
    @NonNull
    public IdentityResolver identities() {
        return identities;
    }

    /** Legt neue Repos an (Anbieter, lokaler Ordner, erster Push). */
    @NonNull
    public RepoCreator repoCreator() {
        return repoCreator;
    }

    /** Löscht Repos vom Gerät. */
    @NonNull
    public RepoRemover repoRemover() {
        return repoRemover;
    }

    /** Die SSH-Schlüssel der App. */
    @NonNull
    public SshKeyStore sshKeys() {
        return sshKeys;
    }

    /** Die SSH-Server, denen die App vertraut. */
    @NonNull
    public KnownHostsStore knownHosts() {
        return knownHosts;
    }

    /** Einstellungen je Repo (Update-Strategie, gewählte Identität). */
    @NonNull
    public RepoSettingsStore repoSettings() {
        return repoSettings;
    }

    /** Darstellung und Netzwerk der App. */
    @NonNull
    public AppSettings settings() {
        return settings;
    }
}
