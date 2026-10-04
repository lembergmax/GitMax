package de.lembergmax.gitmax.provider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Lesehilfen für {@link JSONObject}. {@code optString} liefert bei einem JSON-{@code null} den Text
 * {@code "null"}, das würde als Beschreibung oder Branch-Name im UI landen. Diese Helfer behandeln
 * fehlende und {@code null}-Werte einheitlich.
 */
final class Json {

    private Json() {
    }

    /** Textwert oder {@code null}, wenn der Schlüssel fehlt, {@code null} ist oder nur Leerraum enthält. */
    @Nullable
    static String string(
            @NonNull final JSONObject object,
            @NonNull final String key
    ) {
        if (object.isNull(key)) {
            return null;
        }
        final String value = object.optString(key, "").trim();
        return value.isEmpty() ? null : value;
    }

    /** Textwert, bei fehlendem Wert {@code fallback}. */
    @NonNull
    static String stringOr(
            @NonNull final JSONObject object,
            @NonNull final String key,
            @NonNull final String fallback
    ) {
        final String value = string(object, key);
        return value == null ? fallback : value;
    }
}
