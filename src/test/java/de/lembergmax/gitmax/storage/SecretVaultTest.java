package de.lembergmax.gitmax.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

public final class SecretVaultTest {

    private MemoryKeyValueStore storage;
    private AesTestCipher cipher;
    private SecretVault vault;

    @Before
    public void setUp() {
        storage = new MemoryKeyValueStore();
        cipher = new AesTestCipher();
        vault = new SecretVault(storage, cipher);
    }

    @Test
    public void returnsWhatWasStored() throws Exception {
        vault.put("token.a", "ghp_geheim");

        assertEquals(Optional.of("ghp_geheim"), vault.get("token.a"));
        assertTrue(vault.contains("token.a"));
    }

    @Test
    public void neverStoresThePlaintext() throws Exception {
        vault.put("token.a", "ghp_geheim");

        for (final String stored : storage.raw().values()) {
            assertFalse(stored.contains("ghp_geheim"));
        }
    }

    @Test
    public void sameSecretEncryptsDifferentlyEachTime() throws Exception {
        vault.put("token.a", "gleich");
        final String first = storage.raw().get("secret.token.a");
        vault.put("token.a", "gleich");

        assertFalse(first.equals(storage.raw().get("secret.token.a")));
    }

    @Test
    public void missingSecretIsEmpty() throws Exception {
        assertEquals(Optional.empty(), vault.get("token.fehlt"));
        assertFalse(vault.contains("token.fehlt"));
    }

    @Test
    public void removeDeletesTheSecret() throws Exception {
        vault.put("token.a", "x");
        vault.remove("token.a");

        assertEquals(Optional.empty(), vault.get("token.a"));
    }

    @Test
    public void aSealedValueCannotBeMovedToAnotherName() throws Exception {
        vault.put("token.a", "geheim");
        storage.raw().put("secret.token.b", storage.raw().get("secret.token.a"));

        final VaultException failure = assertThrows(VaultException.class, () -> vault.get("token.b"));

        assertEquals(VaultException.Kind.CORRUPT, failure.kind());
    }

    @Test
    public void damagedBase64IsCorrupt() {
        storage.raw().put("secret.token.a", "%%%kein-base64%%%");

        final VaultException failure = assertThrows(VaultException.class, () -> vault.get("token.a"));

        assertEquals(VaultException.Kind.CORRUPT, failure.kind());
    }

    @Test
    public void lostKeyIsReportedAsUnavailableWithoutLeakingTheSecret() throws Exception {
        vault.put("token.a", "ghp_geheim");
        cipher.makeUnavailable();

        final VaultException failure = assertThrows(VaultException.class, () -> vault.get("token.a"));

        assertEquals(VaultException.Kind.UNAVAILABLE, failure.kind());
        assertFalse(String.valueOf(failure.getMessage()).contains("ghp_geheim"));
    }
}
