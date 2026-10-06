package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.repository.AccountRepository;
import de.lembergmax.gitmax.storage.VaultException;

import org.eclipse.jgit.transport.CredentialsProvider;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Zugangsdaten aus den verknüpften Konten: Das Konto, dessen Host zur Adresse passt, liefert Login und
 * Token. Hat ein Host mehrere Konten, gewinnt das, dessen Login der Besitzer in der Adresse ist.
 */
public final class AccountCredentials implements GitCredentials {

    private final AccountRepository accounts;

    public AccountCredentials(
            @NonNull final AccountRepository accounts
    ) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    @Nullable
    @Override
    public CredentialsProvider forUrl(
            @NonNull final RemoteUrl url
    ) throws GitFailureException {
        if (url.scheme() == RemoteUrl.Scheme.SSH) {
            return null;
        }
        final Optional<Account> account = pick(accounts.all(), url);
        if (account.isEmpty()) {
            return null;
        }
        final boolean cleartext = account.get().endpoint().isInsecure();
        if (url.scheme() == RemoteUrl.Scheme.HTTP && !cleartext) {
            throw new GitFailureException(GitFailureKind.INSECURE_TRANSPORT,
                    "Refusing to send the credentials of account " + account.get().label() + " over plain http", null);
        }
        try {
            final Optional<String> token = accounts.token(account.get().id());
            if (token.isEmpty()) {
                throw new GitFailureException(GitFailureKind.AUTH, "No token stored for account " + account.get().label(), null);
            }
            return new HostBoundCredentials(account.get().endpoint().hostname(), account.get().login(), token.get(), cleartext);
        } catch (final VaultException unreadable) {
            throw new GitFailureException(GitFailureKind.AUTH, "The token of account " + account.get().label() + " is unreadable", unreadable);
        }
    }

    /** Wählt das Konto zur Adresse. */
    public static Optional<Account> pick(
            @NonNull final List<Account> candidates,
            @NonNull final RemoteUrl url
    ) {
        final List<Account> sameHost = candidates.stream()
                .filter(account -> account.endpoint().hostname().equalsIgnoreCase(url.host()))
                .toList();
        if (sameHost.isEmpty()) {
            return Optional.empty();
        }
        final String owner = url.owner().split("/")[0];
        return sameHost.stream()
                .filter(account -> account.login().equalsIgnoreCase(owner))
                .findFirst()
                .or(() -> sameHost.stream().filter(account -> account.status() == Account.Status.ACTIVE).findFirst())
                .or(() -> sameHost.stream().findFirst());
    }
}
