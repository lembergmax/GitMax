package de.lembergmax.gitmax.ui.common;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Objects;

/**
 * Randlos-Darstellung: Inhalte laufen hinter die Systemleisten, jede View, die Inhalt trägt, rückt
 * sich selbst um die Leisten ein. Der ursprüngliche Innenabstand der View bleibt dabei erhalten.
 */
public final class InsetsPadding {

    private InsetsPadding() {
    }

    /**
     * Fügt dem Innenabstand der View die Systemleisten, den Kamera-Ausschnitt und, wenn gewünscht,
     * die Tastatur hinzu.
     *
     * @param top         oben einrücken (Statusleiste)
     * @param bottom      unten einrücken (Navigationsleiste, Tastatur)
     * @param horizontal  seitlich einrücken (Querformat, Ausschnitt)
     */
    public static void apply(
            @NonNull final View view,
            final boolean top,
            final boolean bottom,
            final boolean horizontal
    ) {
        Objects.requireNonNull(view, "view");
        final int initialLeft = view.getPaddingLeft();
        final int initialTop = view.getPaddingTop();
        final int initialRight = view.getPaddingRight();
        final int initialBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            final Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            final Insets keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            target.setPadding(
                    initialLeft + (horizontal ? bars.left : 0),
                    initialTop + (top ? bars.top : 0),
                    initialRight + (horizontal ? bars.right : 0),
                    initialBottom + (bottom ? Math.max(bars.bottom, keyboard.bottom) : 0)
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
