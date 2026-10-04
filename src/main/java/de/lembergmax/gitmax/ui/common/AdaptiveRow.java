package de.lembergmax.gitmax.ui.common;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.R;

import java.util.Objects;

/**
 * Ordnet eine Zeile gleich breiter Buttons bei großer Schrift untereinander an. Nebeneinander passt die Beschriftung
 * ab etwa Schriftgröße 1,5 nicht mehr in die Spalte und bräche mitten im Wort; angeklickter Text soll nie so umbrechen.
 */
public final class AdaptiveRow {

    /** Ab dieser Schriftgröße (Systemeinstellung) stehen die Buttons untereinander. */
    private static final float STACK_FROM_FONT_SCALE = 1.5f;

    private AdaptiveRow() {
    }

    /** {@code true}, wenn die Schrift groß genug ist, dass Spalten nicht mehr taugen. */
    private static boolean isStacked(
            @NonNull final Context context
    ) {
        Objects.requireNonNull(context, "context");
        return context.getResources().getConfiguration().fontScale >= STACK_FROM_FONT_SCALE;
    }

    /**
     * Stellt Ausrichtung, Breiten und Abstände der Zeile für die aktuelle Schriftgröße ein. Nach jeder Änderung der
     * Sichtbarkeit eines Kindes erneut aufrufen, damit die Abstände zu den sichtbaren Kindern passen.
     *
     * @return {@code true}, wenn die Zeile gestapelt wurde
     */
    public static boolean apply(
            @NonNull final LinearLayout row
    ) {
        Objects.requireNonNull(row, "row");
        final boolean stacked = isStacked(row.getContext());
        final int gap = row.getResources().getDimensionPixelSize(R.dimen.space_2);
        row.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        boolean first = true;
        for (int index = 0; index < row.getChildCount(); index += 1) {
            final View child = row.getChildAt(index);
            final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
            params.width = stacked ? ViewGroup.LayoutParams.MATCH_PARENT : 0;
            params.weight = stacked ? 0f : 1f;
            params.setMarginStart(!stacked && !first ? gap : 0);
            params.setMarginEnd(0);
            params.topMargin = stacked && !first ? gap : 0;
            child.setLayoutParams(params);
            if (child.getVisibility() != View.GONE) {
                first = false;
            }
        }
        return stacked;
    }
}
