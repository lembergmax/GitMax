package de.lembergmax.gitmax.git.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/** Ein SSH-Schlüssel ließ sich nicht erzeugen, lesen oder speichern. Die Meldung enthält nie Schlüsselmaterial. */
public final class SshKeyException extends Exception {

    /** Was schiefging. */
    public enum Reason {
        /** Der Schlüssel ist verschlüsselt und es wurde keine Passphrase angegeben. */
        PASSPHRASE_REQUIRED,
        /** Die Passphrase passt nicht zum Schlüssel. */
        WRONG_PASSPHRASE,
        /** Das Format oder die Schlüsselart wird nicht unterstützt. */
        UNSUPPORTED,
        /** Die Datei enthält keinen lesbaren privaten Schlüssel. */
        INVALID,
        /** Der vergebene Name ist leer oder zu lang. */
        INVALID_NAME,
        /** Die Datei ist für einen Schlüssel zu groß. */
        TOO_LARGE,
        /** Dieser Schlüssel ist schon eingerichtet. */
        DUPLICATE,
        /** Das Erzeugen oder Speichern scheiterte. */
        FAILED
    }

    private final Reason reason;

    public SshKeyException(
            @NonNull final Reason reason,
            @NonNull final String message,
            @Nullable final Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    @NonNull
    public Reason reason() {
        return reason;
    }
}
