package de.lembergmax.gitmax.repository;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.NoReplyEmail;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.provider.GitProviderClient;
import de.lembergmax.gitmax.provider.ProviderException;
import de.lembergmax.gitmax.provider.ProviderProfile;
import de.lembergmax.gitmax.storage.AccountJsonCodec;
import de.lembergmax.gitmax.storage.KeyValueStore;
import de.lembergmax.gitmax.storage.SecretVault;
import de.lembergmax.gitmax.storage.VaultException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Verwaltet die verknüpften Konten: Konten liegen als JSON im {@link KeyValueStore}, die Token
 * verschlüsselt im {@link SecretVault}. Netzwerkaufrufe (Token prüfen) blockieren und gehören auf
 * einen Hintergrund-Thread.
 */
public final class AccountRepository {

    private static final String KEY_ACCOUNTS = "accounts.v1";
    private static final String TOKEN_PREFIX = "token.";

    private final KeyValueStore store;
    private final SecretVault vault;
    private final Function<ProviderType, GitProviderClient> clients;
    private final Supplier<String> idGenerator;

    public AccountRepository(
            @NonNull final KeyValueStore store,
            @NonNull final SecretVault vault,
            @NonNull final Function<ProviderType, GitProviderClient> clients,
            @NonNull final Supplier<String> idGenerator
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.vault = Objects.requireNonNull(vault, "vault");
        this.clients = Objects.requireNonNull(clients, "clients");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    @NonNull
    public synchronized List<Account> all() {
        return AccountJsonCodec.decode(store.get(KEY_ACCOUNTS).orElse(null));
    }

    @NonNull
    public synchronized Optional<Account> find(
            @NonNull final String accountId
    ) {
        Objects.requireNonNull(accountId, "accountId");
        return all().stream().filter(account -> account.id().equals(accountId)).findFirst();
    }

    /**
     * Prüft den Token beim Anbieter und verknüpft das Konto. Gehört der Token einem schon
     * verknüpften Nutzer desselben Hosts, wird dessen Konto aktualisiert statt ein zweites angelegt.
     *
     * @return das gespeicherte Konto
     */
    @NonNull
    public Account connect(
            @NonNull final AccountEndpoint endpoint,
            @NonNull final String token
    ) throws ProviderException, VaultException {
        Objects.requireNonNull(endpoint, "endpoint");
        final String cleanToken = requireToken(token);
        final ProviderProfile profile = clients.apply(endpoint.provider()).fetchProfile(endpoint, cleanToken);

        synchronized (this) {
            final List<Account> accounts = all();
            final Optional<Account> existing = accounts.stream()
                    .filter(account -> account.endpoint().host().equals(endpoint.host()) && account.userId() == profile.userId())
                    .findFirst();
            final Account account = existing
                    .map(known -> refreshed(known, profile, endpoint))
                    .orElseGet(() -> created(endpoint, profile));
            vault.put(tokenKey(account.id()), cleanToken);
            accounts.removeIf(known -> known.id().equals(account.id()));
            accounts.add(account);
            save(accounts);
            return account;
        }
    }

    /**
     * Ersetzt den Token eines Kontos (nach Ablauf oder Widerruf). Der neue Token muss demselben
     * Nutzer gehören, sonst bleibt alles unverändert.
     */
    @NonNull
    public Account replaceToken(
            @NonNull final String accountId,
            @NonNull final String token
    ) throws ProviderException, VaultException, AccountMismatchException {
        final Account account = find(accountId).orElseThrow(() -> new IllegalArgumentException("Unknown account: " + accountId));
        final String cleanToken = requireToken(token);
        final ProviderProfile profile = clients.apply(account.endpoint().provider()).fetchProfile(account.endpoint(), cleanToken);
        if (profile.userId() != account.userId()) {
            throw new AccountMismatchException(
                    "The token belongs to " + profile.login() + ", the account to " + account.login(),
                    profile.login()
            );
        }
        synchronized (this) {
            final Account updated = refreshed(account, profile, account.endpoint());
            vault.put(tokenKey(accountId), cleanToken);
            replaceInList(updated);
            return updated;
        }
    }

    /**
     * Der gespeicherte Token. Fehlt er oder ist er nicht lesbar, wird das Konto auf
     * {@code SECRET_LOST} gesetzt.
     */
    @NonNull
    public Optional<String> token(
            @NonNull final String accountId
    ) throws VaultException {
        Objects.requireNonNull(accountId, "accountId");
        try {
            final Optional<String> token = vault.get(tokenKey(accountId));
            if (token.isEmpty()) {
                markStatus(accountId, Account.Status.SECRET_LOST);
            }
            return token;
        } catch (final VaultException unreadable) {
            markStatus(accountId, Account.Status.SECRET_LOST);
            throw unreadable;
        }
    }

    public synchronized void markStatus(
            @NonNull final String accountId,
            @NonNull final Account.Status status
    ) {
        find(accountId)
                .filter(account -> account.status() != status)
                .ifPresent(account -> replaceInList(account.withStatus(status)));
    }

    public synchronized void updateIdentity(
            @NonNull final String accountId,
            @NonNull final CommitIdentity identity
    ) {
        find(accountId).ifPresent(account -> replaceInList(account.withIdentity(identity)));
    }

    /** Entfernt das Konto und seinen Token. Geklonte Repos bleiben unberührt. */
    public synchronized void remove(
            @NonNull final String accountId
    ) {
        Objects.requireNonNull(accountId, "accountId");
        vault.remove(tokenKey(accountId));
        final List<Account> accounts = all();
        accounts.removeIf(account -> account.id().equals(accountId));
        save(accounts);
    }

    private Account created(
            final AccountEndpoint endpoint,
            final ProviderProfile profile
    ) {
        return new Account(
                idGenerator.get(),
                endpoint,
                profile.login(),
                profile.userId(),
                profile.name(),
                identityOf(endpoint, profile),
                profile.scopes(),
                Account.Status.ACTIVE
        );
    }

    private static Account refreshed(
            final Account known,
            final ProviderProfile profile,
            final AccountEndpoint endpoint
    ) {
        return new Account(
                known.id(),
                endpoint,
                profile.login(),
                profile.userId(),
                profile.name(),
                known.identity(),
                profile.scopes(),
                Account.Status.ACTIVE
        );
    }

    /** Identität aus dem Profil; eine unbrauchbare E-Mail des Anbieters wird durch die No-Reply-Adresse ersetzt. */
    private static CommitIdentity identityOf(
            final AccountEndpoint endpoint,
            final ProviderProfile profile
    ) {
        final String email = CommitIdentity.isPlausibleEmail(profile.email())
                ? profile.email()
                : fallbackEmail(endpoint, profile);
        return new CommitIdentity(profile.name().isBlank() ? profile.login() : profile.name(), email);
    }

    private static String fallbackEmail(
            final AccountEndpoint endpoint,
            final ProviderProfile profile
    ) {
        return endpoint.provider() == ProviderType.GITHUB
                ? NoReplyEmail.github(profile.userId(), profile.login())
                : NoReplyEmail.gitlab(profile.userId(), profile.login(), endpoint.host());
    }

    private void replaceInList(
            final Account updated
    ) {
        final List<Account> accounts = new ArrayList<>(all());
        for (int index = 0; index < accounts.size(); index += 1) {
            if (accounts.get(index).id().equals(updated.id())) {
                accounts.set(index, updated);
            }
        }
        save(accounts);
    }

    private void save(
            final List<Account> accounts
    ) {
        store.put(KEY_ACCOUNTS, AccountJsonCodec.encode(accounts));
    }

    private static String requireToken(
            final String token
    ) {
        Objects.requireNonNull(token, "token");
        final String clean = token.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("The token must not be empty");
        }
        return clean;
    }

    private static String tokenKey(
            final String accountId
    ) {
        return TOKEN_PREFIX + accountId;
    }
}
