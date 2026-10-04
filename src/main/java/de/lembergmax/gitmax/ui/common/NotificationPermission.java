package de.lembergmax.gitmax.ui.common;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import de.lembergmax.gitmax.storage.SharedPreferencesKeyValueStore;

/**
 * Entscheidet, ob die App vor dem ersten Vorgang um die Erlaubnis für Benachrichtigungen bitten soll.
 * Gefragt wird genau einmal; ohne Erlaubnis laufen die Vorgänge trotzdem, nur ohne Fortschrittsanzeige
 * in der Benachrichtigung.
 */
public final class NotificationPermission {

    private static final String STORE_NAME = "gitmax_state";
    private static final String KEY_ASKED = "notifications.asked";

    private NotificationPermission() {
    }

    /** {@code true}, wenn die Berechtigung fehlt und noch nie danach gefragt wurde. */
    public static boolean shouldAsk(
            @NonNull final Context context
    ) {
        final boolean granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        return !granted && new SharedPreferencesKeyValueStore(context, STORE_NAME).get(KEY_ASKED).isEmpty();
    }

    /** Merkt, dass gefragt wurde, egal wie die Antwort ausfiel. */
    public static void markAsked(
            @NonNull final Context context
    ) {
        new SharedPreferencesKeyValueStore(context, STORE_NAME).put(KEY_ASKED, "1");
    }

    /** Die Berechtigung, um die gebeten wird. */
    @NonNull
    public static String permission() {
        return Manifest.permission.POST_NOTIFICATIONS;
    }
}
