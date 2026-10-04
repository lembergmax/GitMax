package de.lembergmax.gitmax.ui.common;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;
import java.util.Objects;

/**
 * Die Sprache der Oberfläche. GitMax kennt Englisch (Standard) und Deutsch; die Wahl liegt beim System
 * ({@code LocaleManager}, ab Android 13), AppCompat baut offene Activities beim Wechsel selbst neu auf.
 */
public final class Languages {

    /** Was der Nutzer wählen kann. */
    public enum Choice {
        /** Die Sprache des Geräts (Englisch, wenn GitMax sie nicht kennt). */
        SYSTEM(""),
        ENGLISH("en"),
        GERMAN("de");

        private final String tag;

        Choice(
                @NonNull final String tag
        ) {
            this.tag = tag;
        }

        /** Sprach-Kennung nach BCP 47, leer für „wie das System“. */
        @NonNull
        public String tag() {
            return tag;
        }
    }

    private Languages() {
    }

    /** Die für GitMax gewählte Sprache; {@link Choice#SYSTEM}, wenn keine festgelegt ist. */
    @NonNull
    public static Choice current() {
        final LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty() || locales.get(0) == null) {
            return Choice.SYSTEM;
        }
        return choiceFor(locales.get(0).getLanguage());
    }

    /** Stellt die Sprache ein. */
    public static void apply(
            @NonNull final Choice choice
    ) {
        Objects.requireNonNull(choice, "choice");
        AppCompatDelegate.setApplicationLocales(choice == Choice.SYSTEM
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(choice.tag()));
    }

    /** Ordnet eine Sprache einer Wahl zu; unbekannte Sprachen gelten als „wie das System“. */
    @NonNull
    static Choice choiceFor(
            @NonNull final String language
    ) {
        final String lower = language.toLowerCase(Locale.ROOT);
        for (final Choice choice : Choice.values()) {
            if (!choice.tag().isEmpty() && choice.tag().equals(lower)) {
                return choice;
            }
        }
        return Choice.SYSTEM;
    }
}
