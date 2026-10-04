package de.lembergmax.gitmax.storage;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * {@link SecretCipher} für JVM-Tests: echte AES-GCM-Chiffre mit einem Schlüssel im Arbeitsspeicher
 * und demselben Format wie {@link KeystoreSecretCipher} (IV vor dem Chiffretext, Name als AAD).
 */
public final class AesTestCipher implements SecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();
    private boolean unavailable;

    public AesTestCipher() {
        try {
            final KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            key = generator.generateKey();
        } catch (final GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Simuliert einen verlorenen Keystore-Schlüssel. */
    public void makeUnavailable() {
        unavailable = true;
    }

    @Override
    public byte[] encrypt(
            final byte[] plain,
            final byte[] aad
    ) throws VaultException {
        failIfUnavailable();
        try {
            final byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            final byte[] encrypted = cipher.doFinal(plain);
            final byte[] sealed = new byte[IV_BYTES + encrypted.length];
            System.arraycopy(iv, 0, sealed, 0, IV_BYTES);
            System.arraycopy(encrypted, 0, sealed, IV_BYTES, encrypted.length);
            return sealed;
        } catch (final GeneralSecurityException failure) {
            throw new VaultException(VaultException.Kind.UNAVAILABLE, "Test-Chiffre", failure);
        }
    }

    @Override
    public byte[] decrypt(
            final byte[] sealed,
            final byte[] aad
    ) throws VaultException {
        failIfUnavailable();
        if (sealed.length <= IV_BYTES) {
            throw new VaultException(VaultException.Kind.CORRUPT, "zu kurz", null);
        }
        try {
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(sealed, 0, IV_BYTES)));
            cipher.updateAAD(aad);
            return cipher.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
        } catch (final AEADBadTagException tampered) {
            throw new VaultException(VaultException.Kind.CORRUPT, "manipuliert", tampered);
        } catch (final GeneralSecurityException failure) {
            throw new VaultException(VaultException.Kind.UNAVAILABLE, "Test-Chiffre", failure);
        }
    }

    private void failIfUnavailable() throws VaultException {
        if (unavailable) {
            throw new VaultException(VaultException.Kind.UNAVAILABLE, "Schlüssel verloren", null);
        }
    }
}
