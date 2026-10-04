package de.lembergmax.gitmax.storage;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM mit einem Schlüssel im Android-Keystore. Der Schlüssel verlässt den Keystore nie und
 * verlangt keine Nutzerauthentifizierung (die App hat keine Sperre). Nur Standard-JCA, keine
 * selbstgebauten Primitive.
 */
public final class KeystoreSecretCipher implements SecretCipher {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "gitmax_vault_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BITS = 256;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    @NonNull
    @Override
    public byte[] encrypt(
            @NonNull final byte[] plain,
            @NonNull final byte[] aad
    ) throws VaultException {
        Objects.requireNonNull(plain, "plain");
        Objects.requireNonNull(aad, "aad");
        try {
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey());
            cipher.updateAAD(aad);
            final byte[] iv = cipher.getIV();
            final byte[] encrypted = cipher.doFinal(plain);
            final byte[] sealed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, sealed, 0, iv.length);
            System.arraycopy(encrypted, 0, sealed, iv.length, encrypted.length);
            return sealed;
        } catch (final GeneralSecurityException failure) {
            throw map(failure);
        }
    }

    @NonNull
    @Override
    public byte[] decrypt(
            @NonNull final byte[] sealed,
            @NonNull final byte[] aad
    ) throws VaultException {
        Objects.requireNonNull(sealed, "sealed");
        Objects.requireNonNull(aad, "aad");
        if (sealed.length <= IV_BYTES) {
            throw new VaultException(VaultException.Kind.CORRUPT, "The stored secret is too short", null);
        }
        try {
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, loadOrCreateKey(),
                    new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(sealed, 0, IV_BYTES)));
            cipher.updateAAD(aad);
            return cipher.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
        } catch (final GeneralSecurityException failure) {
            throw map(failure);
        }
    }

    private static SecretKey loadOrCreateKey() throws GeneralSecurityException {
        try {
            final KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            final KeyStore.Entry existing = keyStore.getEntry(KEY_ALIAS, null);
            if (existing instanceof KeyStore.SecretKeyEntry secretKeyEntry) {
                return secretKeyEntry.getSecretKey();
            }
            final KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            generator.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
            )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            return generator.generateKey();
        } catch (final IOException failure) {
            throw new GeneralSecurityException("Keystore unreadable", failure);
        }
    }

    private static VaultException map(
            final GeneralSecurityException failure
    ) {
        if (failure instanceof AEADBadTagException) {
            return new VaultException(VaultException.Kind.CORRUPT, "The secret cannot be decrypted", failure);
        }
        if (failure instanceof KeyPermanentlyInvalidatedException || failure instanceof UnrecoverableKeyException) {
            return new VaultException(VaultException.Kind.UNAVAILABLE, "The keystore key has become invalid", failure);
        }
        return new VaultException(VaultException.Kind.UNAVAILABLE, "Keystore unavailable", failure);
    }
}
