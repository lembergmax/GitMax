package de.lembergmax.gitmax.storage;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.Optional;

/**
 * {@link KeyValueStore} auf einer privaten {@link SharedPreferences}-Datei. Schreibvorgänge nutzen
 * {@code commit()}: Geheimnisse und Konten sollen auch einen sofortigen Prozess-Tod überstehen.
 */
@SuppressLint("ApplySharedPref") // bewusst synchron, siehe oben
public final class SharedPreferencesKeyValueStore implements KeyValueStore {

    private final SharedPreferences preferences;

    public SharedPreferencesKeyValueStore(
            @NonNull final Context context,
            @NonNull final String fileName
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(fileName, "fileName");
        this.preferences = context.getApplicationContext().getSharedPreferences(fileName, Context.MODE_PRIVATE);
    }

    @NonNull
    @Override
    public Optional<String> get(
            @NonNull final String key
    ) {
        return Optional.ofNullable(preferences.getString(key, null));
    }

    @Override
    public void put(
            @NonNull final String key,
            @NonNull final String value
    ) {
        preferences.edit().putString(key, value).commit();
    }

    @Override
    public void remove(
            @NonNull final String key
    ) {
        preferences.edit().remove(key).commit();
    }
}
