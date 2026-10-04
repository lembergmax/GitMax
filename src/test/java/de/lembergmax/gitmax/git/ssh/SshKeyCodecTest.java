package de.lembergmax.gitmax.git.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.SshKeyType;

import org.junit.Test;

import java.security.KeyPair;

/** Prüft Erzeugen, Serialisieren und Lesen von SSH-Schlüsseln, auch solchen, die {@code ssh-keygen} erzeugt hat. */
public final class SshKeyCodecTest {

    @Test
    public void aGeneratedEd25519KeyHasAPublicLineAndAFingerprint() throws Exception {
        final KeyPair pair = SshKeyCodec.generate(SshKeyType.ED25519);

        final String line = SshKeyCodec.publicLine(pair.getPublic(), "mein handy");

        assertTrue(line, line.startsWith("ssh-ed25519 AAAA"));
        assertTrue(line.endsWith(" mein-handy"));
        assertTrue(SshKeyCodec.fingerprint(pair.getPublic()).startsWith("SHA256:"));
        assertEquals(SshKeyType.ED25519, SshKeyCodec.typeOf(pair.getPublic()));
    }

    @Test
    public void aGeneratedRsaKeyIsRsa() throws Exception {
        final KeyPair pair = SshKeyCodec.generate(SshKeyType.RSA);

        assertTrue(SshKeyCodec.publicLine(pair.getPublic(), "").startsWith("ssh-rsa AAAA"));
        assertEquals(SshKeyType.RSA, SshKeyCodec.typeOf(pair.getPublic()));
    }

    @Test
    public void twoGeneratedKeysDiffer() throws Exception {
        final KeyPair first = SshKeyCodec.generate(SshKeyType.ED25519);
        final KeyPair second = SshKeyCodec.generate(SshKeyType.ED25519);

        assertNotEquals(SshKeyCodec.fingerprint(first.getPublic()), SshKeyCodec.fingerprint(second.getPublic()));
    }

    @Test
    public void aSerializedKeyReadsBackAsTheSameKey() throws Exception {
        final KeyPair pair = SshKeyCodec.generate(SshKeyType.ED25519);

        final String text = SshKeyCodec.encodePrivate(pair);
        final KeyPair back = SshKeyCodec.decodePrivate(text, null);

        assertTrue(text.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"));
        assertEquals(SshKeyCodec.fingerprint(pair.getPublic()), SshKeyCodec.fingerprint(back.getPublic()));
    }

    @Test
    public void anRsaKeyReadsBackAsTheSameKey() throws Exception {
        final KeyPair pair = SshKeyCodec.generate(SshKeyType.RSA);

        final KeyPair back = SshKeyCodec.decodePrivate(SshKeyCodec.encodePrivate(pair), null);

        assertEquals(SshKeyCodec.fingerprint(pair.getPublic()), SshKeyCodec.fingerprint(back.getPublic()));
    }

    @Test
    public void anOpenSshKeyFromSshKeygenIsImported() throws Exception {
        final KeyPair pair = SshKeyCodec.decodePrivate(SshFixtures.ED25519_PLAIN, null);

        assertEquals(SshFixtures.ED25519_PLAIN_FINGERPRINT, SshKeyCodec.fingerprint(pair.getPublic()));
    }

    @Test
    public void aProtectedKeyAsksForItsPassphrase() {
        final SshKeyException failure = assertThrows(SshKeyException.class,
                () -> SshKeyCodec.decodePrivate(SshFixtures.ED25519_PROTECTED, null));

        assertEquals(SshKeyException.Reason.PASSPHRASE_REQUIRED, failure.reason());
    }

    @Test
    public void aWrongPassphraseIsRecognized() {
        final SshKeyException failure = assertThrows(SshKeyException.class,
                () -> SshKeyCodec.decodePrivate(SshFixtures.ED25519_PROTECTED, "falsch"));

        assertEquals(SshKeyException.Reason.WRONG_PASSPHRASE, failure.reason());
    }

    @Test
    public void theRightPassphraseUnlocksTheKey() throws Exception {
        final KeyPair pair = SshKeyCodec.decodePrivate(SshFixtures.ED25519_PROTECTED, SshFixtures.PROTECTED_PASSPHRASE);

        assertEquals(SshFixtures.ED25519_PROTECTED_FINGERPRINT, SshKeyCodec.fingerprint(pair.getPublic()));
    }

    @Test
    public void aClassicPemRsaKeyIsImported() throws Exception {
        final KeyPair pair = SshKeyCodec.decodePrivate(SshFixtures.RSA_PEM, null);

        assertEquals(SshFixtures.RSA_PEM_FINGERPRINT, SshKeyCodec.fingerprint(pair.getPublic()));
    }

    @Test
    public void anEcdsaKeyIsImportedAsEcdsa() throws Exception {
        final KeyPair pair = SshKeyCodec.decodePrivate(SshFixtures.ECDSA, null);

        assertEquals(SshFixtures.ECDSA_FINGERPRINT, SshKeyCodec.fingerprint(pair.getPublic()));
        assertEquals(SshKeyType.ECDSA, SshKeyCodec.typeOf(pair.getPublic()));
    }

    @Test
    public void textWithoutAKeyIsInvalid() {
        final SshKeyException failure = assertThrows(SshKeyException.class,
                () -> SshKeyCodec.decodePrivate("das ist keine Schlüsseldatei", null));

        assertEquals(SshKeyException.Reason.INVALID, failure.reason());
    }

    @Test
    public void aPublicKeyIsNotAPrivateKey() {
        final SshKeyException failure = assertThrows(SshKeyException.class,
                () -> SshKeyCodec.decodePrivate(SshFixtures.ED25519_PLAIN_PUBLIC, null));

        assertEquals(SshKeyException.Reason.INVALID, failure.reason());
    }

    @Test
    public void aBrokenKeyBodyIsInvalid() {
        final String broken = "-----BEGIN OPENSSH PRIVATE KEY-----\nAAAA\n-----END OPENSSH PRIVATE KEY-----\n";

        final SshKeyException failure = assertThrows(SshKeyException.class, () -> SshKeyCodec.decodePrivate(broken, null));

        assertTrue(failure.reason() == SshKeyException.Reason.INVALID || failure.reason() == SshKeyException.Reason.UNSUPPORTED);
    }

    @Test
    public void theMessageNeverContainsKeyMaterial() {
        final SshKeyException failure = assertThrows(SshKeyException.class,
                () -> SshKeyCodec.decodePrivate(SshFixtures.ED25519_PROTECTED, "falsch"));

        assertTrue(!String.valueOf(failure.getMessage()).contains("b3BlbnNzaC1r"));
        assertTrue(!String.valueOf(failure.getMessage()).contains("falsch"));
    }
}
