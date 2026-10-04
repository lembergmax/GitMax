package de.lembergmax.gitmax.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Einmalige Mitteilung eines ViewModels an die Oberfläche (Snackbar, Navigation). Wird nach dem
 * ersten Abholen nicht erneut geliefert, z. B. nach einer Drehung des Bildschirms.
 */
public final class Event<T> {

    private final T content;
    private boolean handled;

    public Event(
            @NonNull final T content
    ) {
        this.content = Objects.requireNonNull(content, "content");
    }

    /** Der Inhalt beim ersten Aufruf, danach {@code null}. */
    @Nullable
    public synchronized T consume() {
        if (handled) {
            return null;
        }
        handled = true;
        return content;
    }
}
