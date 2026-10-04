package de.lembergmax.gitmax.git.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.model.SshKeyType;

import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.security.SecurityUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;

/**
 * Erzeugt, serialisiert und liest SSH-Schlüssel (OpenSSH-Format). Der private Schlüssel wird nie in
 * einer Datei abgelegt: er geht als Text in den {@code SecretVault}.
 */
public final class SshKeyCodec {

    private static final int RSA_BITS = 4096;
    private static final int ED25519_BITS = 256;
    private static final int MAX_KEY_TEXT = 64 * 1024;

    private SshKeyCodec() {
    }

    /** Erzeugt ein neues Schlüsselpaar. */
    @NonNull
    public static KeyPair generate(
            @NonNull final SshKeyType type
    ) throws SshKeyException {
        Objects.requireNonNull(type, "type");
        try {
            if (type == SshKeyType.ED25519) {
                if (!SecurityUtils.isEDDSACurveSupported()) {
                    throw new SshKeyException(SshKeyException.Reason.UNSUPPORTED,
                            "Ed25519 is not supported on this device", null);
                }
                return KeyUtils.generateKeyPair("ssh-ed25519", ED25519_BITS);
            }
            return KeyUtils.generateKeyPair("ssh-rsa", RSA_BITS);
        } catch (final GeneralSecurityException failure) {
            throw new SshKeyException(SshKeyException.Reason.FAILED, "The key could not be generated", failure);
        }
    }

    /** Art des Schlüssels; {@code null} bei Arten, die GitMax gar nicht kennt (z. B. DSA). */
    @Nullable
    public static SshKeyType typeOf(
            @NonNull final PublicKey key
    ) {
        final String name = nameOf(key);
        if (name.equals("ssh-ed25519")) {
            return SshKeyType.ED25519;
        }
        if (name.equals("ssh-rsa")) {
            return SshKeyType.RSA;
        }
        if (name.startsWith("ecdsa-sha2-")) {
            return SshKeyType.ECDSA;
        }
        return null;
    }

    /** Name im SSH-Protokoll, z. B. {@code ssh-ed25519}. */
    @NonNull
    public static String nameOf(
            @NonNull final PublicKey key
    ) {
        final String name = KeyUtils.getKeyType(key);
        return name == null ? "" : name;
    }

    /** Öffentlicher Schlüssel als eine Zeile {@code art base64 kommentar}. */
    @NonNull
    public static String publicLine(
            @NonNull final PublicKey key,
            @NonNull final String comment
    ) {
        final String line = PublicKeyEntry.toString(key);
        final String cleaned = comment.replaceAll("\\s+", "-");
        return cleaned.isEmpty() ? line : line + " " + cleaned;
    }

    /** Fingerabdruck {@code SHA256:…} wie ihn GitHub und {@code ssh-keygen -l} anzeigen. */
    @NonNull
    public static String fingerprint(
            @NonNull final PublicKey key
    ) {
        return KeyUtils.getFingerPrint(key);
    }

    /** Der private Schlüssel als unverschlüsselter OpenSSH-Text; er gehört nur in den {@code SecretVault}. */
    @NonNull
    public static String encodePrivate(
            @NonNull final KeyPair pair
    ) throws SshKeyException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(pair, "", (OpenSSHKeyEncryptionContext) null, out);
            return out.toString(StandardCharsets.UTF_8);
        } catch (final IOException | GeneralSecurityException failure) {
            throw new SshKeyException(SshKeyException.Reason.FAILED, "The key could not be serialized", failure);
        }
    }

    /**
     * Liest einen privaten Schlüssel (OpenSSH oder PEM, auch passphrasengeschützt).
     *
     * @param passphrase Passphrase oder {@code null}, wenn der Schlüssel nicht geschützt ist
     */
    @NonNull
    public static KeyPair decodePrivate(
            @NonNull final String text,
            @Nullable final String passphrase
    ) throws SshKeyException {
        Objects.requireNonNull(text, "text");
        if (text.length() > MAX_KEY_TEXT) {
            throw new SshKeyException(SshKeyException.Reason.TOO_LARGE, "The file is too large for a key", null);
        }
        if (!text.contains("PRIVATE KEY")) {
            throw new SshKeyException(SshKeyException.Reason.INVALID, "This is not a private key", null);
        }
        final AskedProvider provider = new AskedProvider(passphrase);
        try {
            final Iterator<KeyPair> pairs = SecurityUtils.loadKeyPairIdentities(
                    null, NamedResource.ofName("import"),
                    new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), provider).iterator();
            if (!pairs.hasNext()) {
                throw new SshKeyException(SshKeyException.Reason.INVALID, "No keys in the file", null);
            }
            return pairs.next();
        } catch (final IOException | GeneralSecurityException failure) {
            throw classify(failure, provider);
        }
    }

    private static SshKeyException classify(
            final Exception failure,
            final AskedProvider provider
    ) {
        final String message = String.valueOf(failure.getMessage()).toLowerCase(Locale.ROOT);
        if (provider.asked && provider.passphrase == null) {
            return new SshKeyException(SshKeyException.Reason.PASSPHRASE_REQUIRED, "The key is protected by a passphrase", failure);
        }
        if (provider.asked || message.contains("password") || message.contains("passphrase") || message.contains("decrypt")) {
            return new SshKeyException(SshKeyException.Reason.WRONG_PASSPHRASE, "The passphrase does not match the key", failure);
        }
        if (message.contains("unsupported") || message.contains("unknown key") || message.contains("no provider")) {
            return new SshKeyException(SshKeyException.Reason.UNSUPPORTED, "This key type is not supported", failure);
        }
        return new SshKeyException(SshKeyException.Reason.INVALID, "The key is unreadable", failure);
    }

    /** Gibt die Passphrase nur einmal her und merkt sich, ob überhaupt eine verlangt wurde. */
    private static final class AskedProvider implements FilePasswordProvider {

        private final String passphrase;
        private boolean asked;

        private AskedProvider(
                @Nullable final String passphrase
        ) {
            this.passphrase = passphrase == null || passphrase.isEmpty() ? null : passphrase;
        }

        @Override
        public String getPassword(
                final SessionContext session,
                final NamedResource resourceKey,
                final int retryIndex
        ) {
            asked = true;
            return retryIndex == 0 ? passphrase : null;
        }

        @Override
        public ResourceDecodeResult handleDecodeAttemptResult(
                final SessionContext session,
                final NamedResource resourceKey,
                final int retryIndex,
                final String password,
                final Exception err
        ) {
            return ResourceDecodeResult.TERMINATE;
        }
    }
}
