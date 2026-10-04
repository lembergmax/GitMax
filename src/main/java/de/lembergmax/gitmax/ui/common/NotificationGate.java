package de.lembergmax.gitmax.ui.common;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Objects;

/**
 * Hält vor dem ersten Git-Vorgang an, um einmal um die Erlaubnis für Benachrichtigungen zu bitten, und
 * führt die Aktion danach aus, egal wie die Antwort ausfiel. Muss beim Erzeugen des Fragments angelegt
 * werden, weil der Berechtigungs-Starter früh registriert sein muss.
 */
public final class NotificationGate {

    private final Fragment fragment;
    private final ActivityResultLauncher<String> launcher;
    @Nullable
    private Runnable pending;

    public NotificationGate(
            @NonNull final Fragment fragment
    ) {
        this.fragment = Objects.requireNonNull(fragment, "fragment");
        this.launcher = fragment.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            final Runnable action = pending;
            pending = null;
            if (action != null) {
                action.run();
            }
        });
    }

    /** Führt {@code action} aus; fragt vorher einmalig nach der Benachrichtigungs-Erlaubnis. */
    public void run(
            @NonNull final Runnable action
    ) {
        if (NotificationPermission.shouldAsk(fragment.requireContext())) {
            NotificationPermission.markAsked(fragment.requireContext());
            pending = action;
            launcher.launch(NotificationPermission.permission());
        } else {
            action.run();
        }
    }
}
