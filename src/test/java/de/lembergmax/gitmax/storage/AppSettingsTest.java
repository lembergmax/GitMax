package de.lembergmax.gitmax.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Prüft Standardwerte, Speichern und das Verhalten bei unlesbaren Werten. */
public final class AppSettingsTest {

    @Test
    public void defaultsAreSystemThemeAndEverythingElseOff() {
        final AppSettings settings = new AppSettings(new MemoryKeyValueStore());

        assertEquals(AppSettings.ThemeMode.SYSTEM, settings.themeMode());
        assertFalse(settings.systemColors());
        assertFalse(settings.wifiOnly());
    }

    @Test
    public void valuesSurviveANewInstanceOnTheSameStore() {
        final MemoryKeyValueStore store = new MemoryKeyValueStore();
        final AppSettings first = new AppSettings(store);
        first.setThemeMode(AppSettings.ThemeMode.DARK);
        first.setSystemColors(true);
        first.setWifiOnly(true);

        final AppSettings second = new AppSettings(store);

        assertEquals(AppSettings.ThemeMode.DARK, second.themeMode());
        assertTrue(second.systemColors());
        assertTrue(second.wifiOnly());
    }

    @Test
    public void switchesCanBeTurnedOffAgain() {
        final AppSettings settings = new AppSettings(new MemoryKeyValueStore());
        settings.setWifiOnly(true);
        settings.setSystemColors(true);

        settings.setWifiOnly(false);
        settings.setSystemColors(false);

        assertFalse(settings.wifiOnly());
        assertFalse(settings.systemColors());
    }

    @Test
    public void unknownThemeFallsBackToSystem() {
        final MemoryKeyValueStore store = new MemoryKeyValueStore();
        store.put("settings.theme", "NEON");

        assertEquals(AppSettings.ThemeMode.SYSTEM, new AppSettings(store).themeMode());
    }
}
