package de.lembergmax.gitmax.git.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.SshKeyInfo;
import de.lembergmax.gitmax.domain.model.SshKeyType;
import de.lembergmax.gitmax.storage.AesTestCipher;
import de.lembergmax.gitmax.storage.MemoryKeyValueStore;
import de.lembergmax.gitmax.storage.SecretVault;

import org.junit.Test;

import java.security.KeyPair;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Prüft, dass SSH-Schlüssel nur verschlüsselt abgelegt werden und sich sauber verwalten lassen. */
public final class SshKeyStoreTest {

    private final MemoryKeyValueStore metadata = new MemoryKeyValueStore();
    private final MemoryKeyValueStore secrets = new MemoryKeyValueStore();
    private final AesTestCipher cipher = new AesTestCipher();
    private final AtomicInteger counter = new AtomicInteger();
    private final SshKeyStore store = new SshKeyStore(metadata, new SecretVault(secrets, cipher), () -> 1000L,
            () -> "id-" + counter.incrementAndGet());

    @Test
    public void aGeneratedKeyIsListedWithItsPublicPart() throws Exception {
        final SshKeyInfo info = store.generate("Handy", SshKeyType.ED25519);

        assertEquals(List.of(info), store.list());
        assertEquals("Handy", info.label());
        assertEquals(SshKeyType.ED25519, info.type());
        assertTrue(info.publicKey().startsWith("ssh-ed25519 AAAA"));
        assertTrue(info.publicKey().endsWith(" Handy"));
        assertTrue(info.fingerprint().startsWith("SHA256:"));
        assertEquals(1000L, info.createdMillis());
        assertFalse(store.isEmpty());
    }

    @Test
    public void thePrivateKeyOnlyLivesEncryptedInTheVault() throws Exception {
        store.generate("Handy", SshKeyType.ED25519);

        for (final String value : metadata.raw().values()) {
            assertFalse(value.contains("PRIVATE KEY"));
        }
        for (final String value : secrets.raw().values()) {
            assertFalse("Chiffretext, nie Klartext", value.contains("PRIVATE KEY"));
        }
        assertEquals(1, secrets.raw().size());
    }

    @Test
    public void keyPairsReturnsUsableKeys() throws Exception {
        final SshKeyInfo info = store.generate("Handy", SshKeyType.ED25519);

        final List<KeyPair> pairs = store.keyPairs();

        assertEquals(1, pairs.size());
        assertEquals(info.fingerprint(), SshKeyCodec.fingerprint(pairs.get(0).getPublic()));
    }

    @Test
    public void importingAKeyFromSshKeygenKeepsItsFingerprint() throws Exception {
        final SshKeyInfo info = store.importKey("Alt", SshFixtures.ED25519_PLAIN, null);

        assertEquals(SshFixtures.ED25519_PLAIN_FINGERPRINT, info.fingerprint());
        assertEquals(SshKeyType.ED25519, info.type());
    }

    @Test
    public void aProtectedKeyIsStoredUnprotectedInTheVaultAfterImport() throws Exception {
        final SshKeyInfo info = store.importKey("Geschützt", SshFixtures.ED25519_PROTECTED, SshFixtures.PROTECTED_PASSPHRASE);

        assertEquals(SshFixtures.ED25519_PROTECTED_FINGERPRINT, info.fingerprint());
        // Beim Verwenden braucht es keine Passphrase mehr: der Schlüssel liegt verschlüsselt im Keystore-Tresor.
        assertEquals(1, store.keyPairs().size());
    }

    @Test
    public void aProtectedKeyWithoutPassphraseIsRefusedAndNothingIsStored() {
        final SshKeyException failure = assertThrows(SshKeyException.class,
                () -> store.importKey("Geschützt", SshFixtures.ED25519_PROTECTED, null));

        assertEquals(SshKeyException.Reason.PASSPHRASE_REQUIRED, failure.reason());
        assertTrue(store.isEmpty());
        assertTrue(secrets.raw().isEmpty());
    }

    @Test
    public void anEcdsaKeyCanBeImported() throws Exception {
        final SshKeyInfo info = store.importKey("EC", SshFixtures.ECDSA, null);

        assertEquals(SshKeyType.ECDSA, info.type());
    }

    @Test
    public void theSameKeyCannotBeAddedTwice() throws Exception {
        store.importKey("Eins", SshFixtures.ED25519_PLAIN, null);

        final SshKeyException failure = assertThrows(SshKeyException.class,
                () -> store.importKey("Zwei", SshFixtures.ED25519_PLAIN, null));

        assertEquals(SshKeyException.Reason.DUPLICATE, failure.reason());
        assertEquals(1, store.list().size());
    }

    @Test
    public void theNameMustNotBeBlankOrTooLong() {
        assertEquals(SshKeyException.Reason.INVALID_NAME,
                assertThrows(SshKeyException.class, () -> store.generate("  ", SshKeyType.ED25519)).reason());
        assertEquals(SshKeyException.Reason.INVALID_NAME,
                assertThrows(SshKeyException.class, () -> store.generate("x".repeat(61), SshKeyType.ED25519)).reason());
        assertTrue(store.isEmpty());
    }

    @Test
    public void ecdsaKeysAreNotGenerated() {
        assertEquals(SshKeyException.Reason.UNSUPPORTED,
                assertThrows(SshKeyException.class, () -> store.generate("EC", SshKeyType.ECDSA)).reason());
    }

    @Test
    public void removingDeletesTheKeyAndItsSecret() throws Exception {
        final SshKeyInfo first = store.generate("Eins", SshKeyType.ED25519);
        final SshKeyInfo second = store.generate("Zwei", SshKeyType.ED25519);

        store.remove(first.id());

        assertEquals(List.of(second), store.list());
        assertEquals(1, secrets.raw().size());
        assertEquals(1, store.keyPairs().size());
    }

    @Test
    public void keysSurviveANewInstanceOnTheSameStorage() throws Exception {
        final SshKeyInfo info = store.generate("Handy", SshKeyType.ED25519);

        final SshKeyStore reopened = new SshKeyStore(metadata, new SecretVault(secrets, cipher), () -> 2000L, () -> "neu");

        assertEquals(List.of(info), reopened.list());
        assertEquals(1, reopened.keyPairs().size());
    }

    @Test
    public void anUnreadableSecretDoesNotLockOutTheOtherKeys() throws Exception {
        store.generate("Kaputt", SshKeyType.ED25519);
        final SshKeyInfo good = store.generate("Gut", SshKeyType.ED25519);
        secrets.raw().put("secret.ssh.key.id-1", "kein-base64!");

        final List<KeyPair> pairs = store.keyPairs();

        assertEquals(1, pairs.size());
        assertEquals(good.fingerprint(), SshKeyCodec.fingerprint(pairs.get(0).getPublic()));
        assertEquals("Der kaputte Schlüssel bleibt zum Löschen in der Liste", 2, store.list().size());
    }

    @Test
    public void damagedMetadataMeansNoKeys() {
        metadata.put("ssh.keys", "{kaputt");

        assertTrue(store.list().isEmpty());
        assertTrue(store.isEmpty());
    }
}
