package de.lembergmax.gitmax.ui.common;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.DialogNewFolderBinding;

import java.util.Locale;

/**
 * Bestätigung für Unwiderrufliches: Der Knopf wird erst frei, wenn der Nutzer ein bestimmtes Wort (meist den Namen des
 * Repos) eintippt. Ein versehentliches Tippen auf „Löschen“ reicht dann nicht.
 */
public final class TypedConfirmDialog {

    private TypedConfirmDialog() {
    }

    /**
     * @param expected Text, der eingegeben werden muss (ohne Beachtung der Groß-/Kleinschreibung)
     */
    public static void show(
            @NonNull final Context context,
            @NonNull final LayoutInflater inflater,
            @StringRes final int title,
            @NonNull final String message,
            @NonNull final String expected,
            @StringRes final int confirm,
            @NonNull final Runnable onConfirm
    ) {
        final DialogNewFolderBinding binding = DialogNewFolderBinding.inflate(inflater);
        binding.nameLayout.setHint(context.getString(R.string.typed_confirm_hint, expected));
        final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setView(binding.getRoot())
                .setPositiveButton(confirm, (shown, which) -> onConfirm.run())
                .setNegativeButton(R.string.activity_cancel, null)
                .create();
        dialog.setOnShowListener(shown -> {
            final Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(false);
            binding.nameInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(
                        final CharSequence text,
                        final int start,
                        final int count,
                        final int after
                ) {
                    // nichts vorzubereiten
                }

                @Override
                public void onTextChanged(
                        final CharSequence text,
                        final int start,
                        final int before,
                        final int count
                ) {
                    positive.setEnabled(matches(text, expected));
                }

                @Override
                public void afterTextChanged(
                        final Editable editable
                ) {
                    // nichts nachzuarbeiten
                }
            });
        });
        dialog.show();
    }

    /** Ob der eingetippte Text dem erwarteten entspricht; Leerraum am Rand und die Schreibweise zählen nicht. */
    static boolean matches(
            @NonNull final CharSequence typed,
            @NonNull final String expected
    ) {
        return typed.toString().strip().toLowerCase(Locale.ROOT).equals(expected.strip().toLowerCase(Locale.ROOT));
    }
}
