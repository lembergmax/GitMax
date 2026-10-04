package de.lembergmax.gitmax.storage;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Einstellungen der App als Ganzes (nicht je Repo oder Konto): Darstellung und Netzwerk. Nichts davon ist geheim.
 * Unbekannte oder fehlende Werte fallen still auf den Standard zurück.
 */
public final class AppSettings {

    /** Wie das Farbschema gewählt wird. */
    public enum ThemeMode {
        /** Hell und Dunkel folgen dem System (Standard). */
        SYSTEM,
        LIGHT,
        DARK
    }

    private static final String KEY_THEME = "settings.theme";
    private static final String KEY_SYSTEM_COLORS = "settings.system_colors";
    private static final String KEY_WIFI_ONLY = "settings.wifi_only";
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    private final KeyValueStore store;

    public AppSettings(
            @NonNull final KeyValueStore store
    ) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @NonNull
    public ThemeMode themeMode() {
        final String raw = store.get(KEY_THEME).orElse("");
        for (final ThemeMode mode : ThemeMode.values()) {
            if (mode.name().equals(raw)) {
                return mode;
            }
        }
        return ThemeMode.SYSTEM;
    }

    public void setThemeMode(
            @NonNull final ThemeMode mode
    ) {
        store.put(KEY_THEME, Objects.requireNonNull(mode, "mode").name());
    }

    /** {@code true}, wenn die Farben vom Hintergrundbild kommen (Android 12 und neuer); standardmäßig aus. */
    public boolean systemColors() {
        return TRUE.equals(store.get(KEY_SYSTEM_COLORS).orElse(FALSE));
    }

    public void setSystemColors(
            final boolean enabled
    ) {
        store.put(KEY_SYSTEM_COLORS, enabled ? TRUE : FALSE);
    }

    /** {@code true}, wenn Vorgänge bei gemessenem Netz (mobile Daten) erst nach einer Bestätigung laufen; standardmäßig aus. */
    public boolean wifiOnly() {
        return TRUE.equals(store.get(KEY_WIFI_ONLY).orElse(FALSE));
    }

    public void setWifiOnly(
            final boolean enabled
    ) {
        store.put(KEY_WIFI_ONLY, enabled ? TRUE : FALSE);
    }
}
