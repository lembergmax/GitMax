package de.lembergmax.gitmax.repository;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.git.AccountCredentials;
import de.lembergmax.gitmax.git.RepoOrigins;
import de.lembergmax.gitmax.storage.RepoSettingsStore;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bestimmt, mit welcher Identität in einem Repo committet wird: die für das Repo gewählte, sonst die des
 * Kontos, zu dem sein Remote gehört, sonst die des ersten Kontos.
 */
public final class IdentityResolver {

    private final AccountRepository accounts;
    private final RepoSettingsStore settings;

    public IdentityResolver(
            @NonNull final AccountRepository accounts,
            @NonNull final RepoSettingsStore settings
    ) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Die Identität für das Repo; leer, wenn weder eine gewählt noch ein Konto verbunden ist. */
    @NonNull
    public Optional<CommitIdentity> resolve(
            @NonNull final File repo
    ) {
        final Optional<CommitIdentity> chosen = settings.identity(repo);
        if (chosen.isPresent()) {
            return chosen;
        }
        final List<Account> all = accounts.all();
        final Optional<RemoteUrl> origin = RepoOrigins.originOf(repo);
        if (origin.isPresent()) {
            final Optional<Account> match = AccountCredentials.pick(all, origin.get());
            if (match.isPresent()) {
                return Optional.of(match.get().identity());
            }
        }
        return all.stream().findFirst().map(Account::identity);
    }

    /** Merkt die Identität für dieses Repo. */
    public void choose(
            @NonNull final File repo,
            @NonNull final CommitIdentity identity
    ) {
        settings.setIdentity(repo, identity);
    }
}
