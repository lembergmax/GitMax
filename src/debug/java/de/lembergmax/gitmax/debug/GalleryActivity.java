package de.lembergmax.gitmax.debug;

import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.AttrRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ui.common.InsetsPadding;
import de.lembergmax.gitmax.ui.common.PebbleView;

/**
 * Nur im Debug-Build: zeigt Farbrollen, Schrift, Buttons, Chips, Eingaben und alle Kieselstein-Zustände
 * zur Screenshot-Prüfung (Hell/Dunkel, Schriftgröße). Alle Beispiele sind synthetisch.
 */
public final class GalleryActivity extends AppCompatActivity {

    private static final int SWATCH_PADDING_DP = 12;
    private static final int SWATCH_MARGIN_DP = 4;
    private static final long DEMO_PROGRESS_MS = 3_000L;

    private PebbleView rowPebble;

    @Override
    protected void onCreate(
            @Nullable final Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_gallery);

        InsetsPadding.apply(findViewById(R.id.scroll), true, true, true);
        fillSwatches();
        fillTypeSamples();
        fillPebbles();
        wireInteractions();
    }

    private void fillSwatches() {
        final LinearLayout host = findViewById(R.id.swatches);
        addSwatch(host, "primary / onPrimary", androidx.appcompat.R.attr.colorPrimary, com.google.android.material.R.attr.colorOnPrimary);
        addSwatch(host, "primaryContainer", com.google.android.material.R.attr.colorPrimaryContainer, com.google.android.material.R.attr.colorOnPrimaryContainer);
        addSwatch(host, "secondaryContainer", com.google.android.material.R.attr.colorSecondaryContainer, com.google.android.material.R.attr.colorOnSecondaryContainer);
        addSwatch(host, "tertiaryContainer", com.google.android.material.R.attr.colorTertiaryContainer, com.google.android.material.R.attr.colorOnTertiaryContainer);
        addSwatch(host, "errorContainer", com.google.android.material.R.attr.colorErrorContainer, com.google.android.material.R.attr.colorOnErrorContainer);
        addSwatch(host, "surfaceContainerHighest", com.google.android.material.R.attr.colorSurfaceContainerHighest, com.google.android.material.R.attr.colorOnSurface);
        addSwatch(host, "surfaceContainer", com.google.android.material.R.attr.colorSurfaceContainer, com.google.android.material.R.attr.colorOnSurface);
        addSwatch(host, "surface", com.google.android.material.R.attr.colorSurface, com.google.android.material.R.attr.colorOnSurface);
        addSwatch(host, "gitAdded", R.attr.colorGitAddedContainer, R.attr.colorOnGitAddedContainer);
        addSwatch(host, "gitModified", R.attr.colorGitModifiedContainer, R.attr.colorOnGitModifiedContainer);
        addSwatch(host, "gitConflict", R.attr.colorGitConflictContainer, R.attr.colorOnGitConflictContainer);
        addSwatch(host, "diffAdded", R.attr.colorDiffAddedBackground, com.google.android.material.R.attr.colorOnSurface);
        addSwatch(host, "diffRemoved", R.attr.colorDiffRemovedBackground, com.google.android.material.R.attr.colorOnSurface);
    }

    private void addSwatch(
            final LinearLayout host,
            final String label,
            @AttrRes final int background,
            @AttrRes final int foreground
    ) {
        final TextView tile = new TextView(this);
        final GradientDrawable shape = new GradientDrawable();
        shape.setColor(MaterialColors.getColor(tile, background));
        shape.setCornerRadius(getResources().getDimension(R.dimen.radius_m));
        tile.setBackground(shape);
        tile.setText(label);
        // Erst die Schriftrolle, dann die Farbe: setTextAppearance überschreibt die Textfarbe.
        tile.setTextAppearance(resolveStyle(com.google.android.material.R.attr.textAppearanceLabelLarge));
        tile.setTextColor(MaterialColors.getColor(tile, foreground));
        final int padding = dp(SWATCH_PADDING_DP);
        tile.setPadding(padding, padding, padding, padding);
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(SWATCH_MARGIN_DP);
        host.addView(tile, params);
    }

    private void fillTypeSamples() {
        final LinearLayout host = findViewById(R.id.type_samples);
        addType(host, "Display Medium", com.google.android.material.R.attr.textAppearanceDisplayMedium);
        addType(host, "Headline Small", com.google.android.material.R.attr.textAppearanceHeadlineSmall);
        addType(host, "Title Large", com.google.android.material.R.attr.textAppearanceTitleLarge);
        addType(host, "Title Medium", com.google.android.material.R.attr.textAppearanceTitleMedium);
        addType(host, "Body Large: Klonen und Aktualisieren", com.google.android.material.R.attr.textAppearanceBodyLarge);
        addType(host, "Body Medium: 12 Repos, 3 geändert, 2 voraus", com.google.android.material.R.attr.textAppearanceBodyMedium);
        addType(host, "Label Large", com.google.android.material.R.attr.textAppearanceLabelLarge);
        addType(host, "Label Small 0123456789", com.google.android.material.R.attr.textAppearanceLabelSmall);
    }

    private void addType(
            final LinearLayout host,
            final String text,
            @AttrRes final int appearance
    ) {
        final TextView view = new TextView(this);
        view.setText(text);
        view.setTextAppearance(resolveStyle(appearance));
        view.setTextColor(MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface));
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(SWATCH_MARGIN_DP);
        host.addView(view, params);
    }

    private void fillPebbles() {
        final GridLayout grid = findViewById(R.id.pebbles);
        final int size = getResources().getDimensionPixelSize(R.dimen.pebble_size_header);
        for (final PebbleView.Status status : PebbleView.Status.values()) {
            final LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);

            final PebbleView pebble = new PebbleView(this);
            pebble.setStatus(status);
            if (status == PebbleView.Status.RUNNING) {
                pebble.setProgress(0.62f);
            }
            pebble.setContentDescription(status.name());
            cell.addView(pebble, new LinearLayout.LayoutParams(size, size));

            final TextView label = new TextView(this);
            label.setText(status.name());
            label.setTextAppearance(resolveStyle(com.google.android.material.R.attr.textAppearanceLabelMedium));
            label.setTextColor(MaterialColors.getColor(label, com.google.android.material.R.attr.colorOnSurfaceVariant));
            cell.addView(label);

            final GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            params.width = 0;
            params.bottomMargin = dp(SWATCH_PADDING_DP);
            grid.addView(cell, params);
        }
    }

    private void wireInteractions() {
        rowPebble = findViewById(R.id.row_pebble);
        rowPebble.setStatus(PebbleView.Status.AHEAD);

        final View pressed = findViewById(R.id.button_pressed);
        pressed.post(() -> pressed.setPressed(true));

        findViewById(R.id.button_snackbar).setOnClickListener(view ->
                Snackbar.make(view, R.string.gallery_snackbar_text, Snackbar.LENGTH_LONG)
                        .setAction(R.string.gallery_snackbar_action, action -> { })
                        .show());

        findViewById(R.id.button_progress).setOnClickListener(view -> runDemoProgress());
        findViewById(R.id.button_indeterminate).setOnClickListener(view -> {
            rowPebble.setStatus(PebbleView.Status.RUNNING);
            rowPebble.setIndeterminate(true);
        });

        final TextInputLayout errorField = (TextInputLayout) findViewById(R.id.field_error).getParent().getParent();
        errorField.setError(getString(R.string.gallery_error_text));

        ((TextView) findViewById(R.id.code_sample)).setText(
                "a1b2c3d  Merge branch 'main'\n9f8e7d6  Fix clone on shared storage\nsrc/main/java/de/lembergmax/gitmax/git/JgitEngine.java");
    }

    private void runDemoProgress() {
        rowPebble.setStatus(PebbleView.Status.RUNNING);
        rowPebble.setProgress(0f);
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DEMO_PROGRESS_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(update -> rowPebble.setProgress((float) update.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(
                    final android.animation.Animator finished
            ) {
                rowPebble.setStatus(PebbleView.Status.DONE);
            }
        });
        animator.start();
    }

    private int resolveStyle(
            @AttrRes final int attribute
    ) {
        final TypedValue value = new TypedValue();
        getTheme().resolveAttribute(attribute, value, true);
        return value.resourceId;
    }

    private int dp(
            final int value
    ) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
