package de.lembergmax.gitmax.ui.common;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.DialogIdentityBinding;
import de.lembergmax.gitmax.domain.model.CommitIdentity;

import java.util.function.Consumer;

/** Fragt Name und E-Mail für die Commit-Identität eines Repos ab. */
public final class IdentityDialog {

    private IdentityDialog() {
    }

    /**
     * Zeigt den Dialog. Er schließt erst, wenn Name und Adresse brauchbar sind.
     *
     * @param current   vorbelegte Identität oder {@code null}
     * @param onSaved   erhält die bestätigte Identität
     */
    public static void show(
            @NonNull final Context context,
            @NonNull final LayoutInflater inflater,
            @Nullable final CommitIdentity current,
            @NonNull final Consumer<CommitIdentity> onSaved
    ) {
        final DialogIdentityBinding binding = DialogIdentityBinding.inflate(inflater);
        if (current != null) {
            binding.nameInput.setText(current.name());
            binding.emailInput.setText(current.email());
        }
        final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.identity_dialog_title)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.identity_save, null)
                .setNegativeButton(R.string.activity_cancel, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            final String name = String.valueOf(binding.nameInput.getText()).trim();
            final String email = String.valueOf(binding.emailInput.getText()).trim();
            binding.nameLayout.setError(name.isEmpty() ? context.getString(R.string.account_detail_error_name) : null);
            binding.emailLayout.setError(CommitIdentity.isPlausibleEmail(email)
                    ? null : context.getString(R.string.account_detail_error_email));
            if (name.isEmpty() || !CommitIdentity.isPlausibleEmail(email)) {
                return;
            }
            dialog.dismiss();
            onSaved.accept(new CommitIdentity(name, email));
        }));
        dialog.show();
    }
}
