package de.lembergmax.gitmax.ui.common;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

import de.lembergmax.gitmax.storage.AppSettings;

import java.util.Objects;

/** Setzt die Darstellungs-Einstellungen um: Hell/Dunkel für die ganze App, Systemfarben je Activity. */
public final class Appearance {

    private Appearance() {
    }

    /** Stellt das Farbschema ein; AppCompat baut offene Activities selbst neu auf, wenn es sich ändert. */
    public static void applyNightMode(
            @NonNull final AppSettings.ThemeMode mode
    ) {
        Objects.requireNonNull(mode, "mode");
        switch (mode) {
            case LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    /**
     * Legt die Farben des Systems über das Theme der Activity, wenn der Nutzer sie will. Muss nach dem Splash-Theme und vor
     * {@code setContentView} laufen, sonst überschreibt das Theme der App die dynamischen Farben wieder.
     */
    public static void applySystemColors(
            @NonNull final Activity activity,
            @NonNull final AppSettings settings
    ) {
        if (settings.systemColors()) {
            DynamicColors.applyToActivityIfAvailable(activity);
        }
    }

    /** {@code true}, wenn das Gerät Systemfarben liefert (Android 12 und neuer). */
    public static boolean systemColorsAvailable() {
        return DynamicColors.isDynamicColorAvailable();
    }
}
