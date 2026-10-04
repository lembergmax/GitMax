package de.lembergmax.gitmax.repository;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.RepoNames;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.CommitRequest;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.NewRepo;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.domain.model.PushRequest;
import de.lembergmax.gitmax.domain.model.RemoteRepo;
import de.lembergmax.gitmax.git.GitEngine;
import de.lembergmax.gitmax.git.GitProgress;
import de.lembergmax.gitmax.git.RepoOrigins;
import de.lembergmax.gitmax.provider.GitProviderClient;
import de.lembergmax.gitmax.provider.ProviderException;
import de.lembergmax.gitmax.storage.VaultException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Legt ein neues Repo an: erst beim Anbieter (scheitert das, bleibt auf dem Telefon nichts liegen), dann der lokale Ordner
 * mit Remote, auf Wunsch eine {@code README.md} als erster Commit und der erste Push. Ein Wiederholen nach einem Fehler
 * beim Push legt nichts doppelt an: Findet sich im Zielordner schon ein Repo mit dem erwarteten Remote, geht es dort weiter.
 */
public final class RepoCreator {

    private static final String BRANCH = "main";
    private static final String README = "README.md";
    private static final String FIRST_COMMIT = "Initial commit";

    /**
     * Was angelegt werden soll.
     *
     * @param accountId   Konto, bei dem das Repo entsteht
     * @param name        Name des Repos (und des Ordners)
     * @param description Beschreibung, darf leer sein
     * @param isPrivate   nicht öffentlich sichtbar
     * @param withReadme  eine {@code README.md} anlegen, committen und pushen
     * @param target      Ordner des neuen Repos; darf nicht existieren oder muss leer sein
     */
    public record Request(
            @NonNull String accountId,
            @NonNull String name,
            @NonNull String description,
            boolean isPrivate,
            boolean withReadme,
            @NonNull File target
    ) {

        public Request {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(target, "target");
        }
    }

    private final AccountRepository accounts;
    private final Function<ProviderType, GitProviderClient> clients;

    public RepoCreator(
            @NonNull final AccountRepository accounts,
            @NonNull final Function<ProviderType, GitProviderClient> clients
    ) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.clients = Objects.requireNonNull(clients, "clients");
    }

    /** Führt alle Schritte aus; blockiert und gehört auf einen Arbeits-Thread. */
    public void create(
            @NonNull final Request request,
            @NonNull final GitEngine engine,
            @NonNull final GitProgress progress
    ) throws GitFailureException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(progress, "progress");
        if (RepoNames.check(request.name()).isPresent()) {
            throw new GitFailureException(GitFailureKind.INVALID_NAME, "Invalid repo name: " + request.name(), null);
        }
        final Account account = accounts.find(request.accountId()).orElseThrow(() ->
                new GitFailureException(GitFailureKind.AUTH, "The account is no longer connected", null));

        if (!continuesEarlierAttempt(request, account)) {
            requireFreeTarget(request.target());
            progress.onProgress(GitProgress.Phase.CONNECTING, GitProgress.UNKNOWN);
            final RemoteRepo created = createAtProvider(request, account);
            engine.initRepository(request.target(), BRANCH, created.httpsUrl());
        }
        if (!request.withReadme()) {
            return;
        }
        progress.onProgress(GitProgress.Phase.WORKING, GitProgress.UNKNOWN);
        writeReadme(request);
        engine.stage(request.target(), List.of(README));
        try {
            engine.commit(new CommitRequest(request.target(), FIRST_COMMIT, account.identity(), false));
        } catch (final GitFailureException failure) {
            // Beim Wiederholen ist die README schon committet: dann geht es direkt zum Push.
            if (failure.kind() != GitFailureKind.NOTHING_TO_COMMIT) {
                throw failure;
            }
        }
        engine.push(new PushRequest(request.target(), false, false), progress);
    }

    private RemoteRepo createAtProvider(
            final Request request,
            final Account account
    ) throws GitFailureException {
        final String token;
        try {
            token = accounts.token(account.id()).orElseThrow(() ->
                    new GitFailureException(GitFailureKind.AUTH, "No token stored for account " + account.label(), null));
        } catch (final VaultException unreadable) {
            throw new GitFailureException(GitFailureKind.AUTH, "The token of account " + account.label() + " is unreadable", unreadable);
        }
        try {
            return clients.apply(account.endpoint().provider())
                    .createRepository(account.id(), account.endpoint(), token,
                            new NewRepo(request.name(), request.description().strip(), request.isPrivate()));
        } catch (final ProviderException failure) {
            throw failureOf(failure);
        }
    }

    private static GitFailureException failureOf(
            final ProviderException failure
    ) {
        final GitFailureKind kind;
        switch (failure.kind()) {
            case UNAUTHORIZED:
            case FORBIDDEN:
                kind = GitFailureKind.AUTH;
                break;
            case NAME_TAKEN:
                kind = GitFailureKind.ALREADY_EXISTS;
                break;
            case INVALID:
                kind = GitFailureKind.INVALID_NAME;
                break;
            case NETWORK:
            case RATE_LIMITED:
            case SERVER:
                kind = GitFailureKind.NETWORK;
                break;
            case NOT_FOUND:
                kind = GitFailureKind.NOT_FOUND;
                break;
            case MALFORMED:
            default:
                kind = GitFailureKind.UNKNOWN;
                break;
        }
        return new GitFailureException(kind, failure.getMessage(), failure);
    }

    /** Im Zielordner liegt schon ein Repo mit dem Remote, das dieser Auftrag anlegt: ein früherer Versuch kam nur bis zum Push. */
    private static boolean continuesEarlierAttempt(
            final Request request,
            final Account account
    ) {
        if (!new File(request.target(), ".git").isDirectory()) {
            return false;
        }
        final Optional<RemoteUrl> origin = RepoOrigins.originOf(request.target());
        return origin.isPresent()
                && origin.get().host().equalsIgnoreCase(account.endpoint().hostname())
                && origin.get().path().equalsIgnoreCase(account.login() + "/" + request.name());
    }

    private static void requireFreeTarget(
            final File target
    ) throws GitFailureException {
        final String[] content = target.list();
        if (target.exists() && (!target.isDirectory() || content == null || content.length > 0)) {
            throw new GitFailureException(GitFailureKind.ALREADY_EXISTS,
                    "The target folder already exists and is not empty: " + target.getAbsolutePath(), null);
        }
    }

    private static void writeReadme(
            final Request request
    ) throws GitFailureException {
        final File readme = new File(request.target(), README);
        if (readme.exists()) {
            return;
        }
        final String description = request.description().strip();
        final String text = "# " + request.name() + "\n" + (description.isEmpty() ? "" : "\n" + description + "\n");
        try {
            Files.write(readme.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (final IOException failure) {
            throw new GitFailureException(GitFailureKind.INVALID_PATH, "Could not create README.md", failure);
        }
    }
}
