package de.lembergmax.gitmax.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.domain.model.RemoteRepo;
import de.lembergmax.gitmax.provider.GitProviderClient;
import de.lembergmax.gitmax.provider.ProviderException;
import de.lembergmax.gitmax.provider.ProviderProfile;
import de.lembergmax.gitmax.storage.AesTestCipher;
import de.lembergmax.gitmax.storage.JsonFileStore;
import de.lembergmax.gitmax.storage.MemoryKeyValueStore;
import de.lembergmax.gitmax.storage.RemoteRepoJsonCodec;
import de.lembergmax.gitmax.storage.SecretVault;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

public final class RemoteRepoRepositoryTest {

    private static final AccountEndpoint GITHUB = AccountEndpoint.fromHostInput(ProviderType.GITHUB, "").orElseThrow();
    private static final RemoteRepo REPO = new RemoteRepo(
            "ignoriert", "7", "alpha", "max/alpha", "", false, false, false, "main",
            "https://github.com/max/alpha.git", "git@github.com:max/alpha.git", "https://github.com/max/alpha", 0L);

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private FakeProviderClient client;
    private AccountRepository accounts;
    private RemoteRepoRepository repos;
    private Account account;

    @Before
    public void setUp() throws Exception {
        client = new FakeProviderClient().returnsProfile(new ProviderProfile("max", 42L, "Max", "max@example.org", List.of()));
        accounts = new AccountRepository(
                new MemoryKeyValueStore(), new SecretVault(new MemoryKeyValueStore(), new AesTestCipher()),
                provider -> client, () -> "konto-1");
        account = accounts.connect(GITHUB, "token");
        repos = new RemoteRepoRepository(new JsonFileStore(new File(folder.getRoot(), "cache")), accounts, provider -> client, () -> 5_000L);
    }

    @Test
    public void nothingIsCachedBeforeTheFirstRefresh() {
        final RemoteRepoJsonCodec.Snapshot snapshot = repos.cached(account.id());

        assertTrue(snapshot.repos().isEmpty());
        assertEquals(0L, snapshot.fetchedAtMillis());
    }

    @Test
    public void refreshReturnsAndCachesTheList() throws Exception {
        client.returnsRepos(List.of(REPO));

        final RemoteRepoJsonCodec.Snapshot fresh = repos.refresh(account, GitProviderClient.ListListener.NONE);

        assertEquals(5_000L, fresh.fetchedAtMillis());
        assertEquals(List.of(REPO), fresh.repos());
        assertEquals(List.of(REPO), repos.cached(account.id()).repos());
    }

    @Test
    public void rejectedTokenMarksTheAccountAndKeepsTheOldCache() throws Exception {
        client.returnsRepos(List.of(REPO));
        repos.refresh(account, GitProviderClient.ListListener.NONE);
        client.failsListWith(new ProviderException(ProviderException.Kind.UNAUTHORIZED, "abgelehnt", null));

        assertThrows(ProviderException.class, () -> repos.refresh(account, GitProviderClient.ListListener.NONE));

        assertEquals(Account.Status.TOKEN_REJECTED, accounts.find(account.id()).orElseThrow().status());
        assertEquals(List.of(REPO), repos.cached(account.id()).repos());
    }

    @Test
    public void successfulRefreshReactivatesARejectedAccount() throws Exception {
        accounts.markStatus(account.id(), Account.Status.TOKEN_REJECTED);
        client.returnsRepos(List.of(REPO));

        repos.refresh(accounts.find(account.id()).orElseThrow(), GitProviderClient.ListListener.NONE);

        assertEquals(Account.Status.ACTIVE, accounts.find(account.id()).orElseThrow().status());
    }

    @Test
    public void refreshWithoutStoredTokenIsUnauthorized() throws Exception {
        accounts.remove(account.id());

        final ProviderException failure = assertThrows(
                ProviderException.class,
                () -> repos.refresh(account, GitProviderClient.ListListener.NONE)
        );

        assertEquals(ProviderException.Kind.UNAUTHORIZED, failure.kind());
    }

    @Test
    public void forgetDropsTheCache() throws Exception {
        client.returnsRepos(List.of(REPO));
        repos.refresh(account, GitProviderClient.ListListener.NONE);

        repos.forget(account.id());

        assertTrue(repos.cached(account.id()).repos().isEmpty());
    }
}
