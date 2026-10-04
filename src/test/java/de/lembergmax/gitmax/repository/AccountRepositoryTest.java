package de.lembergmax.gitmax.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.provider.ProviderException;
import de.lembergmax.gitmax.provider.ProviderProfile;
import de.lembergmax.gitmax.storage.AesTestCipher;
import de.lembergmax.gitmax.storage.MemoryKeyValueStore;
import de.lembergmax.gitmax.storage.SecretVault;
import de.lembergmax.gitmax.storage.VaultException;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class AccountRepositoryTest {

    private static final AccountEndpoint GITHUB = AccountEndpoint.fromHostInput(ProviderType.GITHUB, "").orElseThrow();
    private static final AccountEndpoint GITLAB = AccountEndpoint.fromHostInput(ProviderType.GITLAB, "").orElseThrow();

    private MemoryKeyValueStore store;
    private MemoryKeyValueStore vaultStorage;
    private AesTestCipher cipher;
    private FakeProviderClient client;
    private AccountRepository repository;

    @Before
    public void setUp() {
        store = new MemoryKeyValueStore();
        vaultStorage = new MemoryKeyValueStore();
        cipher = new AesTestCipher();
        client = new FakeProviderClient().returnsProfile(profile("max", 42L, "Max Lemberg", "max@example.org"));
        final AtomicInteger ids = new AtomicInteger();
        repository = new AccountRepository(
                store,
                new SecretVault(vaultStorage, cipher),
                provider -> client,
                () -> "konto-" + ids.incrementAndGet()
        );
    }

    @Test
    public void connectStoresTheAccountAndTheTokenSeparately() throws Exception {
        final Account account = repository.connect(GITHUB, "  ghp_geheim  ");

        assertEquals("konto-1", account.id());
        assertEquals("max", account.login());
        assertEquals(new CommitIdentity("Max Lemberg", "max@example.org"), account.identity());
        assertEquals(Account.Status.ACTIVE, account.status());
        assertEquals(Optional.of("ghp_geheim"), repository.token(account.id()));
        assertFalse("Token darf nicht im Konten-JSON stehen", store.raw().values().toString().contains("ghp_geheim"));
        assertEquals(List.of("ghp_geheim"), client.tokensSeen());
    }

    @Test
    public void connectingTheSameUserAgainUpdatesInsteadOfDuplicating() throws Exception {
        final Account first = repository.connect(GITHUB, "token-alt");
        repository.updateIdentity(first.id(), new CommitIdentity("Anderer Name", "anders@example.org"));

        final Account second = repository.connect(GITHUB, "token-neu");

        assertEquals(first.id(), second.id());
        assertEquals(1, repository.all().size());
        assertEquals("die gewählte Identität bleibt erhalten", "Anderer Name", second.identity().name());
        assertEquals(Optional.of("token-neu"), repository.token(first.id()));
    }

    @Test
    public void sameUserIdOnAnotherHostIsAnotherAccount() throws Exception {
        repository.connect(GITHUB, "t1");
        repository.connect(GITLAB, "t2");

        assertEquals(2, repository.all().size());
    }

    @Test
    public void unusableProviderEmailFallsBackToTheNoReplyAddress() throws Exception {
        client.returnsProfile(profile("max", 42L, "Max", "kein-email"));

        final Account account = repository.connect(GITHUB, "t");

        assertEquals("42+max@users.noreply.github.com", account.identity().email());
    }

    @Test
    public void rejectedTokenStoresNothing() {
        client.failsProfileWith(new ProviderException(ProviderException.Kind.UNAUTHORIZED, "abgelehnt", null));

        final ProviderException failure = assertThrows(ProviderException.class, () -> repository.connect(GITHUB, "falsch"));

        assertEquals(ProviderException.Kind.UNAUTHORIZED, failure.kind());
        assertTrue(repository.all().isEmpty());
        assertTrue(vaultStorage.raw().isEmpty());
    }

    @Test
    public void blankTokenIsRejectedBeforeAnyNetworkCall() {
        assertThrows(IllegalArgumentException.class, () -> repository.connect(GITHUB, "   "));

        assertTrue(client.tokensSeen().isEmpty());
    }

    @Test
    public void replaceTokenKeepsTheAccountAndReactivatesIt() throws Exception {
        final Account account = repository.connect(GITHUB, "alt");
        repository.markStatus(account.id(), Account.Status.TOKEN_REJECTED);

        final Account updated = repository.replaceToken(account.id(), "neu");

        assertEquals(Account.Status.ACTIVE, updated.status());
        assertEquals(Optional.of("neu"), repository.token(account.id()));
    }

    @Test
    public void replaceTokenOfAnotherUserIsRefusedAndKeepsTheOldToken() throws Exception {
        final Account account = repository.connect(GITHUB, "alt");
        client.returnsProfile(profile("fremd", 99L, "Fremd", "fremd@example.org"));

        assertThrows(AccountMismatchException.class, () -> repository.replaceToken(account.id(), "fremder-token"));

        assertEquals(Optional.of("alt"), repository.token(account.id()));
    }

    @Test
    public void lostVaultKeyMarksTheAccountSecretLost() throws Exception {
        final Account account = repository.connect(GITHUB, "t");
        cipher.makeUnavailable();

        assertThrows(VaultException.class, () -> repository.token(account.id()));

        assertEquals(Account.Status.SECRET_LOST, repository.find(account.id()).orElseThrow().status());
    }

    @Test
    public void missingTokenMarksTheAccountSecretLost() throws Exception {
        final Account account = repository.connect(GITHUB, "t");
        vaultStorage.raw().clear();

        assertEquals(Optional.empty(), repository.token(account.id()));

        assertEquals(Account.Status.SECRET_LOST, repository.find(account.id()).orElseThrow().status());
    }

    @Test
    public void removeDeletesAccountAndToken() throws Exception {
        final Account account = repository.connect(GITHUB, "t");

        repository.remove(account.id());

        assertTrue(repository.all().isEmpty());
        assertTrue(vaultStorage.raw().isEmpty());
        assertEquals(Optional.empty(), repository.find(account.id()));
    }

    @Test
    public void updateIdentityPersists() throws Exception {
        final Account account = repository.connect(GITHUB, "t");

        repository.updateIdentity(account.id(), new CommitIdentity("Neu", "neu@example.org"));

        assertEquals("neu@example.org", repository.find(account.id()).orElseThrow().identity().email());
    }

    private static ProviderProfile profile(
            final String login,
            final long id,
            final String name,
            final String email
    ) {
        return new ProviderProfile(login, id, name, email, List.of("repo"));
    }
}
