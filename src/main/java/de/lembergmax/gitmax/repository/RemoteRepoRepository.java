package de.lembergmax.gitmax.repository;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.domain.model.RemoteRepo;
import de.lembergmax.gitmax.provider.GitProviderClient;
import de.lembergmax.gitmax.provider.ProviderException;
import de.lembergmax.gitmax.storage.JsonFileStore;
import de.lembergmax.gitmax.storage.RemoteRepoJsonCodec;
import de.lembergmax.gitmax.storage.VaultException;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Repo-Listen der Konten: eine zwischengespeicherte Fassung zum sofortigen Anzeigen und das
 * Aktualisieren über die API des Anbieters. Aktualisieren blockiert und gehört auf einen
 * Hintergrund-Thread.
 */
public final class RemoteRepoRepository {

    private static final String FILE_PREFIX = "repos_";
    private static final String FILE_SUFFIX = ".json";

    private final JsonFileStore files;
    private final AccountRepository accounts;
    private final Function<ProviderType, GitProviderClient> clients;
    private final LongSupplier clock;

    public RemoteRepoRepository(
            @NonNull final JsonFileStore files,
            @NonNull final AccountRepository accounts,
            @NonNull final Function<ProviderType, GitProviderClient> clients,
            @NonNull final LongSupplier clock
    ) {
        this.files = Objects.requireNonNull(files, "files");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.clients = Objects.requireNonNull(clients, "clients");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Zuletzt gespeicherte Liste eines Kontos; leer mit Zeitpunkt 0, wenn es keine gibt. */
    @NonNull
    public RemoteRepoJsonCodec.Snapshot cached(
            @NonNull final String accountId
    ) {
        return RemoteRepoJsonCodec.decode(files.read(fileName(accountId)).orElse(null));
    }

    /**
     * Lädt die Liste frisch vom Anbieter und speichert sie. Lehnt der Anbieter den Token ab, wird das
     * Konto als {@code TOKEN_REJECTED} markiert.
     */
    @NonNull
    public RemoteRepoJsonCodec.Snapshot refresh(
            @NonNull final Account account,
            @NonNull final GitProviderClient.ListListener listener
    ) throws ProviderException, VaultException, IOException {
        Objects.requireNonNull(account, "account");
        final Optional<String> token = accounts.token(account.id());
        if (token.isEmpty()) {
            throw new ProviderException(ProviderException.Kind.UNAUTHORIZED, "No token stored for the account", null);
        }
        final List<RemoteRepo> repos;
        try {
            repos = clients.apply(account.endpoint().provider())
                    .listRepositories(account.id(), account.endpoint(), token.get(), listener);
        } catch (final ProviderException failure) {
            if (failure.kind() == ProviderException.Kind.UNAUTHORIZED) {
                accounts.markStatus(account.id(), Account.Status.TOKEN_REJECTED);
            }
            throw failure;
        }
        if (account.status() == Account.Status.TOKEN_REJECTED) {
            accounts.markStatus(account.id(), Account.Status.ACTIVE);
        }
        final RemoteRepoJsonCodec.Snapshot snapshot = new RemoteRepoJsonCodec.Snapshot(clock.getAsLong(), repos);
        files.write(fileName(account.id()), RemoteRepoJsonCodec.encode(snapshot));
        return snapshot;
    }

    /** Löscht die gespeicherte Liste, z. B. wenn das Konto entfernt wurde. */
    public void forget(
            @NonNull final String accountId
    ) {
        files.delete(fileName(accountId));
    }

    private static String fileName(
            final String accountId
    ) {
        return FILE_PREFIX + accountId + FILE_SUFFIX;
    }
}
