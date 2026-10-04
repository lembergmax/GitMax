package de.lembergmax.gitmax.ui.common;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.domain.model.HostKeyChallenge;
import de.lembergmax.gitmax.git.ssh.KnownHostsStore;

import java.util.Optional;

/**
 * Zeigt den Fingerabdruck eines SSH-Servers, an dem ein Vorgang gescheitert ist, und lässt den Nutzer entscheiden.
 * Vertraut er, merkt sich der {@link KnownHostsStore} den Schlüssel und der Aufrufer wiederholt den Vorgang.
 */
public final class HostKeyDialog {

    private HostKeyDialog() {
    }

    /**
     * @param hostId    Kennung des Servers aus dem Fehler
     * @param feedback  Ansicht für Hinweise (Snackbar)
     * @param onTrusted läuft, nachdem der Nutzer vertraut hat oder wenn es nichts mehr zu bestätigen gibt
     */
    public static void show(
            @NonNull final Context context,
            @NonNull final KnownHostsStore hosts,
            @NonNull final String hostId,
            @NonNull final View feedback,
            @NonNull final Runnable onTrusted
    ) {
        final Optional<HostKeyChallenge> pending = hosts.pending(hostId);
        if (pending.isEmpty()) {
            // Die Anfrage ist weg (z. B. nach einem Neustart): ein neuer Versuch stellt sie wieder her.
            Snackbar.make(feedback, R.string.hostkey_gone, Snackbar.LENGTH_LONG).show();
            onTrusted.run();
            return;
        }
        final HostKeyChallenge challenge = pending.get();
        final String type = challenge.keyType();
        final String body = challenge.changed()
                ? context.getString(R.string.hostkey_body_changed, String.join("\n", challenge.knownFingerprints()), type, challenge.fingerprint())
                : context.getString(R.string.hostkey_body_unknown, challenge.host(), type, challenge.fingerprint());
        final String message = challenge.contradictsPublished()
                ? context.getString(R.string.hostkey_contradiction) + "\n\n" + body
                : body;
        final int trust = challenge.contradictsPublished() ? R.string.hostkey_trust_anyway
                : challenge.changed() ? R.string.hostkey_trust_changed : R.string.hostkey_trust;
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(challenge.changed() ? R.string.hostkey_title_changed : R.string.hostkey_title_unknown, challenge.host()))
                .setMessage(message)
                .setPositiveButton(trust, (dialog, which) -> {
                    hosts.trust(challenge);
                    onTrusted.run();
                })
                .setNegativeButton(R.string.activity_cancel, null)
                .setNeutralButton(R.string.hostkey_copy, (dialog, which) -> {
                    final ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.hostkey_copy), challenge.fingerprint()));
                    Snackbar.make(feedback, R.string.hostkey_copied, Snackbar.LENGTH_SHORT).show();
                })
                .show();
    }
}
