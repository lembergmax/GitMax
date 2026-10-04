package de.lembergmax.gitmax.storage;

import androidx.annotation.NonNull;

import java.util.Optional;

/**
 * Einfacher Text-Schlüssel-Wert-Speicher. Abstrahiert {@code SharedPreferences}, damit Klassen darüber
 * in JVM-Tests mit einem Speicher im Arbeitsspeicher laufen.
 */
public interface KeyValueStore {

    @NonNull
    Optional<String> get(
            @NonNull String key
    );

    /** Schreibt den Wert und kehrt erst zurück, wenn er dauerhaft gespeichert ist. */
    void put(
            @NonNull String key,
            @NonNull String value
    );

    void remove(
            @NonNull String key
    );
}
