package de.lembergmax.gitmax.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.ProviderType;

import org.junit.Test;

import java.util.List;

public final class AccountJsonCodecTest {

    private static final AccountEndpoint GITHUB = AccountEndpoint.fromHostInput(ProviderType.GITHUB, "").orElseThrow();

    @Test
    public void roundTripKeepsEveryField() {
        final Account account = account("id-1", Account.Status.TOKEN_REJECTED, List.of("repo", "workflow"));

        final List<Account> decoded = AccountJsonCodec.decode(AccountJsonCodec.encode(List.of(account)));

        assertEquals(List.of(account), decoded);
    }

    @Test
    public void encodedAccountsNeverContainATokenField() {
        final String json = AccountJsonCodec.encode(List.of(account("id-1", Account.Status.ACTIVE, List.of())));

        assertFalse(json.toLowerCase().contains("token"));
    }

    @Test
    public void damagedEntryIsSkippedAndTheOthersSurvive() {
        final Account good = account("id-2", Account.Status.ACTIVE, List.of());
        final String encoded = AccountJsonCodec.encode(List.of(good));
        final String json = encoded.substring(0, encoded.length() - 1) + ",{\"id\":\"kaputt\"}]";

        assertEquals(List.of(good), AccountJsonCodec.decode(json));
    }

    @Test
    public void unknownStatusFallsBackToActive() {
        final String json = AccountJsonCodec.encode(List.of(account("id-3", Account.Status.ACTIVE, List.of())))
                .replace("\"ACTIVE\"", "\"GIBT_ES_NICHT\"");

        assertEquals(Account.Status.ACTIVE, AccountJsonCodec.decode(json).get(0).status());
    }

    @Test
    public void garbageAndEmptyInputGiveNoAccounts() {
        assertTrue(AccountJsonCodec.decode(null).isEmpty());
        assertTrue(AccountJsonCodec.decode("").isEmpty());
        assertTrue(AccountJsonCodec.decode("{kein array").isEmpty());
    }

    private static Account account(
            final String id,
            final Account.Status status,
            final List<String> scopes
    ) {
        return new Account(id, GITHUB, "max", 42L, "Max Lemberg", new CommitIdentity("Max", "max@example.org"), scopes, status);
    }
}
