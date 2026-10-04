package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.repository.AccountRepository;
import de.lembergmax.gitmax.storage.AesTestCipher;
import de.lembergmax.gitmax.storage.MemoryKeyValueStore;
import de.lembergmax.gitmax.storage.SecretVault;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

public final class AccountCredentialsTest {

    private static final AccountEndpoint GITHUB = AccountEndpoint.fromHostInput(ProviderType.GITHUB, "").orElseThrow();
    private static final AccountEndpoint GITLAB_COM = AccountEndpoint.fromHostInput(ProviderType.GITLAB, "").orElseThrow();
    private static final AccountEndpoint GITLAB_OWN = AccountEndpoint.fromHostInput(ProviderType.GITLAB, "git.example.org").orElseThrow();

    @Test
    public void noAccountMeansAnonymousAccess() throws Exception {
        final AccountRepository empty = new AccountRepository(new MemoryKeyValueStore(),
                new SecretVault(new MemoryKeyValueStore(), new AesTestCipher()), provider -> null, () -> "id");

        assertNull(new AccountCredentials(empty).forUrl(url("https://github.com/max/alpha.git")));
    }

    @Test
    public void sshAddressesGetNoHttpCredentials() throws Exception {
        final AccountRepository empty = new AccountRepository(new MemoryKeyValueStore(),
                new SecretVault(new MemoryKeyValueStore(), new AesTestCipher()), provider -> null, () -> "id");

        assertNull(new AccountCredentials(empty).forUrl(url("git@github.com:max/alpha.git")));
    }

    @Test
    public void accountOfTheSameHostIsChosen() {
        final Account github = account("1", GITHUB, "max", Account.Status.ACTIVE);
        final Account gitlab = account("2", GITLAB_COM, "max", Account.Status.ACTIVE);

        assertEquals(Optional.of(github), AccountCredentials.pick(List.of(gitlab, github), url("https://github.com/max/alpha.git")));
        assertEquals(Optional.of(gitlab), AccountCredentials.pick(List.of(gitlab, github), url("https://gitlab.com/max/alpha.git")));
    }

    @Test
    public void selfHostedAccountMatchesItsOwnHostOnly() {
        final Account own = account("1", GITLAB_OWN, "max", Account.Status.ACTIVE);

        assertEquals(Optional.of(own), AccountCredentials.pick(List.of(own), url("https://git.example.org/team/app.git")));
        assertTrue(AccountCredentials.pick(List.of(own), url("https://gitlab.com/team/app.git")).isEmpty());
    }

    @Test
    public void ownerLoginWinsWhenTwoAccountsShareAHost() {
        final Account first = account("1", GITHUB, "max", Account.Status.ACTIVE);
        final Account second = account("2", GITHUB, "firma", Account.Status.ACTIVE);

        assertEquals(Optional.of(second), AccountCredentials.pick(List.of(first, second), url("https://github.com/Firma/app.git")));
        assertEquals(Optional.of(first), AccountCredentials.pick(List.of(first, second), url("https://github.com/max/app.git")));
    }

    @Test
    public void activeAccountWinsOverARejectedOneWhenTheOwnerMatchesNeither() {
        final Account rejected = account("1", GITHUB, "alt", Account.Status.TOKEN_REJECTED);
        final Account active = account("2", GITHUB, "neu", Account.Status.ACTIVE);

        assertEquals(Optional.of(active), AccountCredentials.pick(List.of(rejected, active), url("https://github.com/fremd/app.git")));
    }

    @Test
    public void aRejectedAccountIsStillUsedWhenItIsTheOnlyOne() {
        final Account rejected = account("1", GITHUB, "alt", Account.Status.TOKEN_REJECTED);

        assertEquals(Optional.of(rejected), AccountCredentials.pick(List.of(rejected), url("https://github.com/fremd/app.git")));
    }

    private static RemoteUrl url(
            final String raw
    ) {
        return RemoteUrl.parse(raw).orElseThrow();
    }

    private static Account account(
            final String id,
            final AccountEndpoint endpoint,
            final String login,
            final Account.Status status
    ) {
        return new Account(id, endpoint, login, 1L, login, new CommitIdentity(login, login + "@example.invalid"),
                List.of(), status);
    }
}
