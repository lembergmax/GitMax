package de.lembergmax.gitmax.ui.advanced;

import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import de.lembergmax.gitmax.BuildConfig;
import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.DialogPromptBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Baut aus einer {@link ListSource.Prompt} einen Eingabedialog und liefert die Eingaben beim Bestätigen. */
public final class PromptDialog {

    private PromptDialog() {
    }

    public static void show(
            @NonNull final Context context,
            @NonNull final LayoutInflater inflater,
            @NonNull final ListSource.Prompt prompt,
            @NonNull final Consumer<ListSource.Values> onConfirm
    ) {
        final DialogPromptBinding binding = DialogPromptBinding.inflate(inflater);
        final List<TextInputLayout> layouts = new ArrayList<>();
        final List<TextInputEditText> inputs = new ArrayList<>();
        final List<MaterialSwitch> switches = new ArrayList<>();
        for (final ListSource.Field field : prompt.fields()) {
            final TextInputLayout layout = new TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
            layout.setHint(context.getString(field.hint()));
            final TextInputEditText input = new TextInputEditText(layout.getContext());
            input.setText(field.initial());
            input.setSelection(field.initial().length());
            if (field.secret()) {
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            } else {
                input.setInputType(field.multiLine()
                        ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            }
            input.setMinLines(field.multiLine() ? 3 : 1);
            layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (field.secret()) {
                layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
            }
            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = layouts.isEmpty() ? 0 : Math.round(12 * context.getResources().getDisplayMetrics().density);
            binding.container.addView(layout, params);
            layouts.add(layout);
            inputs.add(input);
        }
        for (final ListSource.Toggle toggle : prompt.toggles()) {
            final MaterialSwitch view = new MaterialSwitch(context);
            view.setText(toggle.label());
            view.setChecked(toggle.checked());
            view.setMinHeight(context.getResources().getDimensionPixelSize(R.dimen.touch_target));
            binding.container.addView(view);
            switches.add(view);
        }
        final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(prompt.title())
                .setView(binding.getRoot())
                .setPositiveButton(prompt.confirm(), null)
                .setNegativeButton(R.string.activity_cancel, null)
                .create();
        // Eine Passphrase soll nicht in Bildschirmfotos oder der Übersicht laufender Apps landen (in den Prüfbuilds aus, damit
        // Screenshots der Prüfläufe möglich sind).
        if (!BuildConfig.TEST_BUILD && containsSecret(prompt) && dialog.getWindow() != null) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        dialog.setOnShowListener(shown -> {
            if (!inputs.isEmpty()) {
                inputs.get(0).requestFocus();
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            final List<String> texts = new ArrayList<>();
            boolean valid = true;
            for (int index = 0; index < inputs.size(); index += 1) {
                final String text = String.valueOf(inputs.get(index).getText());
                final boolean missing = prompt.fields().get(index).required() && text.isBlank();
                layouts.get(index).setError(missing ? context.getString(R.string.files_error_invalid_name) : null);
                valid &= !missing;
                texts.add(text);
            }
            if (!valid) {
                return;
            }
            final List<Boolean> toggles = new ArrayList<>();
            for (final MaterialSwitch view : switches) {
                toggles.add(view.isChecked());
            }
            dialog.dismiss();
            onConfirm.accept(new ListSource.Values(texts, toggles));
            });
        });
        dialog.show();
    }

    private static boolean containsSecret(
            @NonNull final ListSource.Prompt prompt
    ) {
        for (final ListSource.Field field : prompt.fields()) {
            if (field.secret()) {
                return true;
            }
        }
        return false;
    }
}
