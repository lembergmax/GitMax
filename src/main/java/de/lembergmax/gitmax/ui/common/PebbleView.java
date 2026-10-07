package de.lembergmax.gitmax.ui.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;

import de.lembergmax.gitmax.R;

import java.util.Objects;

/**
 * Das Kieselstein-Glyph eines Repos: ein weicher Stein, dessen Umriss zugleich der Statusring ist.
 *
 * <p>Der Ring zeigt den Zustand, ohne dass Farbe allein ihn trägt: Lage des Bogens (oben = lokal
 * voraus, unten = Remote voraus), gestrichelt bei Konflikt und ein Symbol in der Mitte. Bei Klon
 * und Update wird derselbe Ring zur Fortschrittsanzeige und kippt am Ende in ein Häkchen.</p>
 */
public final class PebbleView extends View {

    /** Zustand eines Repos oder eines laufenden Vorgangs. */
    public enum Status {
        /** Auf dem Stand des Remotes, nichts zu tun. */
        CLEAN,
        /** Lokale Änderungen vorhanden. */
        CHANGED,
        /** Lokale Commits, die noch nicht gepusht sind. */
        AHEAD,
        /** Remote hat neue Commits. */
        BEHIND,
        /** Beides: lokal und remote weitergegangen. */
        DIVERGED,
        /** Ungelöste Merge-Konflikte. */
        CONFLICT,
        /** Ein Vorgang läuft; der Ring zeigt Fortschritt. */
        RUNNING,
        /** Vorgang erfolgreich beendet. */
        DONE,
        /** Vorgang fehlgeschlagen. */
        FAILED
    }

    /** Anteil des Pfades für Bögen: Start oben, im Uhrzeigersinn. */
    private static final float HALF = 0.5f;
    private static final float INDETERMINATE_ARC = 0.25f;
    private static final long INDETERMINATE_PERIOD_MS = 1400L;
    private static final float STROKE_RATIO = 0.075f;
    private static final float GLYPH_RATIO = 0.44f;
    private static final float GLYPH_SCALE_FROM = 0.25f;

    /** Kieselstein-Umriss im 100×100-Raster: flach, breiter als hoch und leicht schief; Start oben mittig. */
    private static final String PEBBLE_PATH_DATA =
            "M50,12 C70,8 94,22 96,44 C98,68 78,88 52,88 C26,88 4,76 4,52 C4,30 28,14 50,12 Z";

    private final Path pebblePath = new Path();
    private final Path segment = new Path();
    private final Matrix matrix = new Matrix();
    private final PathMeasure measure = new PathMeasure();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float pathLength;
    private float strokeWidth;

    private Status status = Status.CLEAN;
    private float progress;
    private boolean indeterminate;
    private float indeterminatePhase;

    private Visual from = new Visual();
    private Visual current = new Visual();
    private Visual target = new Visual();

    private Drawable glyph;
    private Drawable previousGlyph;
    private float glyphTransition = 1f;

    @Nullable
    private ValueAnimator transitionAnimator;
    @Nullable
    private ValueAnimator glyphAnimator;
    @Nullable
    private ValueAnimator spinAnimator;

    public PebbleView(
            @NonNull final Context context
    ) {
        this(context, null);
    }

    public PebbleView(
            @NonNull final Context context,
            @Nullable final AttributeSet attrs
    ) {
        super(context, attrs);
        fillPaint.setStyle(Paint.Style.FILL);
        trackPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        applyStatusImmediately(Status.CLEAN);
    }

    /**
     * Setzt den Zustand. Bei sichtbarer View wird weich übergeblendet, sonst sofort gesetzt.
     */
    public void setStatus(
            @NonNull final Status newStatus
    ) {
        Objects.requireNonNull(newStatus, "newStatus");
        if (newStatus == status) {
            return;
        }
        status = newStatus;
        if (isAttachedToWindow() && isShown() && getWidth() > 0) {
            animateTo(buildVisual(newStatus), glyphFor(newStatus));
        } else {
            applyStatusImmediately(newStatus);
        }
        updateSpinner();
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    /**
     * Fortschritt von 0 bis 1; wirkt nur im Zustand {@link Status#RUNNING}. Schaltet auf bestimmte
     * Anzeige um.
     */
    public void setProgress(
            final float fraction
    ) {
        progress = Math.max(0f, Math.min(1f, fraction));
        indeterminate = false;
        if (status == Status.RUNNING) {
            target = buildVisual(Status.RUNNING);
            current = target.copy();
            from = target.copy();
            updateSpinner();
            invalidate();
        }
    }

    /** Unbestimmter Fortschritt: ein kurzer Bogen läuft um den Stein. */
    public void setIndeterminate(
            final boolean value
    ) {
        indeterminate = value;
        if (status == Status.RUNNING) {
            target = buildVisual(Status.RUNNING);
            current = target.copy();
            from = target.copy();
        }
        updateSpinner();
        invalidate();
    }

    @Override
    protected void onSizeChanged(
            final int width,
            final int height,
            final int oldWidth,
            final int oldHeight
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        final float side = Math.min(width, height);
        strokeWidth = side * STROKE_RATIO;
        final float inset = strokeWidth;
        final RectF bounds = new RectF();
        final Path unit = new Path();
        buildUnit(unit);
        unit.computeBounds(bounds, true);
        final float scale = (side - 2 * inset) / Math.max(bounds.width(), bounds.height());
        matrix.reset();
        matrix.postTranslate(-bounds.left, -bounds.top);
        matrix.postScale(scale, scale);
        matrix.postTranslate(
                (width - bounds.width() * scale) / 2f,
                (height - bounds.height() * scale) / 2f
        );
        pebblePath.reset();
        unit.transform(matrix, pebblePath);
        measure.setPath(pebblePath, true);
        pathLength = measure.getLength();
        trackPaint.setStrokeWidth(strokeWidth);
        arcPaint.setStrokeWidth(strokeWidth);
        invalidate();
    }

    @Override
    protected void onDraw(
            @NonNull final Canvas canvas
    ) {
        super.onDraw(canvas);
        if (pathLength <= 0f) {
            return;
        }
        fillPaint.setColor(current.fillColor);
        canvas.drawPath(pebblePath, fillPaint);

        trackPaint.setColor(current.trackColor);
        canvas.drawPath(pebblePath, trackPaint);

        drawArc(canvas, current.arcAStart, current.arcAEnd, current.arcAColor, current.dashed);
        drawArc(canvas, current.arcBStart, current.arcBEnd, current.arcBColor, false);
        drawGlyph(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        settleTransitions();
        cancelAnimators();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateSpinner();
    }

    private void drawArc(
            final Canvas canvas,
            final float start,
            final float end,
            @ColorInt final int color,
            final boolean dashed
    ) {
        if (end - start <= 0.001f || android.graphics.Color.alpha(color) == 0) {
            return;
        }
        arcPaint.setColor(color);
        arcPaint.setPathEffect(dashed ? new DashPathEffect(
                new float[]{strokeWidth * 1.6f, strokeWidth * 1.4f}, 0f) : null);
        segment.reset();
        final float startLength = start * pathLength;
        final float endLength = end * pathLength;
        if (endLength <= pathLength) {
            measure.getSegment(startLength, endLength, segment, true);
        } else {
            // Bogen läuft über den Pfadanfang hinaus (nur beim unbestimmten Fortschritt).
            measure.getSegment(startLength, pathLength, segment, true);
            final Path wrapped = new Path();
            measure.getSegment(0f, endLength - pathLength, wrapped, true);
            segment.addPath(wrapped);
        }
        canvas.drawPath(segment, arcPaint);
        arcPaint.setPathEffect(null);
    }

    private void drawGlyph(
            final Canvas canvas
    ) {
        final float size = Math.min(getWidth(), getHeight()) * GLYPH_RATIO;
        final float centerX = getWidth() / 2f;
        final float centerY = getHeight() / 2f;
        if (previousGlyph != null && glyphTransition < 1f) {
            drawGlyphAt(canvas, previousGlyph, size, centerX, centerY,
                    1f - glyphTransition, lerp(1f, GLYPH_SCALE_FROM, glyphTransition));
        }
        if (glyph != null) {
            drawGlyphAt(canvas, glyph, size, centerX, centerY,
                    glyphTransition, lerp(GLYPH_SCALE_FROM, 1f, glyphTransition));
        }
    }

    private void drawGlyphAt(
            final Canvas canvas,
            final Drawable drawable,
            final float size,
            final float centerX,
            final float centerY,
            final float alpha,
            final float scale
    ) {
        final float half = size * scale / 2f;
        drawable.setBounds(
                Math.round(centerX - half),
                Math.round(centerY - half),
                Math.round(centerX + half),
                Math.round(centerY + half)
        );
        drawable.setAlpha(Math.round(255 * Math.max(0f, Math.min(1f, alpha))));
        drawable.draw(canvas);
    }

    private void animateTo(
            @NonNull final Visual newTarget,
            @DrawableRes final int glyphRes
    ) {
        if (transitionAnimator != null) {
            transitionAnimator.cancel();
        }
        from = current.copy();
        target = newTarget;
        transitionAnimator = ValueAnimator.ofFloat(0f, 1f);
        transitionAnimator.setDuration(Motion.duration(getContext(), R.integer.motion_fast));
        transitionAnimator.setInterpolator(Motion.smoothOut(getContext()));
        transitionAnimator.addUpdateListener(animator -> {
            current = Visual.mix(from, target, (float) animator.getAnimatedValue());
            invalidate();
        });
        transitionAnimator.start();
        swapGlyph(glyphRes, target.glyphColor);
    }

    private void applyStatusImmediately(
            @NonNull final Status newStatus
    ) {
        status = newStatus;
        target = buildVisual(newStatus);
        current = target.copy();
        from = target.copy();
        previousGlyph = null;
        glyphTransition = 1f;
        glyph = loadGlyph(glyphFor(newStatus), target.glyphColor);
        invalidate();
    }

    private void swapGlyph(
            @DrawableRes final int glyphRes,
            @ColorInt final int color
    ) {
        previousGlyph = glyph;
        glyph = loadGlyph(glyphRes, color);
        glyphTransition = 0f;
        if (glyphAnimator != null) {
            glyphAnimator.cancel();
        }
        glyphAnimator = ValueAnimator.ofFloat(0f, 1f);
        glyphAnimator.setDuration(Motion.duration(getContext(), R.integer.motion_fast));
        glyphAnimator.setInterpolator(Motion.easeInOut(getContext()));
        glyphAnimator.addUpdateListener(animator -> {
            glyphTransition = (float) animator.getAnimatedValue();
            invalidate();
        });
        glyphAnimator.start();
    }

    @Nullable
    private Drawable loadGlyph(
            @DrawableRes final int resource,
            @ColorInt final int color
    ) {
        final Drawable loaded = AppCompatResources.getDrawable(getContext(), resource);
        if (loaded == null) {
            return null;
        }
        final Drawable copy = loaded.getConstantState() != null
                ? loaded.getConstantState().newDrawable(getResources()).mutate()
                : loaded.mutate();
        copy.setTint(color);
        return copy;
    }

    private void updateSpinner() {
        final boolean shouldSpin = status == Status.RUNNING && indeterminate
                && isAttachedToWindow() && !Motion.isReduced(getContext());
        if (shouldSpin && spinAnimator == null) {
            spinAnimator = ValueAnimator.ofFloat(0f, 1f);
            spinAnimator.setDuration(INDETERMINATE_PERIOD_MS);
            spinAnimator.setInterpolator(new LinearInterpolator());
            spinAnimator.setRepeatCount(ValueAnimator.INFINITE);
            spinAnimator.addUpdateListener(animator -> {
                indeterminatePhase = (float) animator.getAnimatedValue();
                target = buildVisual(Status.RUNNING);
                current = target.copy();
                invalidate();
            });
            spinAnimator.start();
        } else if (!shouldSpin && spinAnimator != null) {
            spinAnimator.cancel();
            spinAnimator = null;
        }
    }

    /**
     * Setzt einen unterbrochenen Übergang auf seinen Endzustand. Ein abgebrochener Animator liefert keinen letzten Wert;
     * eine Zeile, die beim Scrollen aus dem Fenster fällt, bliebe sonst halb umgefärbt, weil ein erneutes Binden mit
     * demselben Zustand nichts mehr ändert.
     */
    private void settleTransitions() {
        if (transitionAnimator != null && transitionAnimator.isRunning()) {
            current = target.copy();
        }
        if (glyphAnimator != null && glyphAnimator.isRunning()) {
            glyphTransition = 1f;
            previousGlyph = null;
        }
    }

    private void cancelAnimators() {
        if (transitionAnimator != null) {
            transitionAnimator.cancel();
            transitionAnimator = null;
        }
        if (glyphAnimator != null) {
            glyphAnimator.cancel();
            glyphAnimator = null;
        }
        if (spinAnimator != null) {
            spinAnimator.cancel();
            spinAnimator = null;
        }
    }

    @NonNull
    private Visual buildVisual(
            @NonNull final Status forStatus
    ) {
        final Visual visual = new Visual();
        visual.trackColor = color(com.google.android.material.R.attr.colorOutlineVariant);
        switch (forStatus) {
            case CHANGED:
                visual.fillColor = color(R.attr.colorGitModifiedContainer);
                visual.glyphColor = color(R.attr.colorGitModified);
                visual.arcAEnd = 1f;
                visual.arcAColor = visual.glyphColor;
                break;
            case AHEAD:
                visual.fillColor = color(com.google.android.material.R.attr.colorPrimaryContainer);
                visual.glyphColor = color(androidx.appcompat.R.attr.colorPrimary);
                visual.arcAEnd = HALF;
                visual.arcAColor = visual.glyphColor;
                break;
            case BEHIND:
                visual.fillColor = color(com.google.android.material.R.attr.colorTertiaryContainer);
                visual.glyphColor = color(com.google.android.material.R.attr.colorTertiary);
                visual.arcAStart = HALF;
                visual.arcAEnd = 1f;
                visual.arcAColor = visual.glyphColor;
                break;
            case DIVERGED:
                visual.fillColor = color(com.google.android.material.R.attr.colorSurfaceContainerHighest);
                visual.glyphColor = color(com.google.android.material.R.attr.colorOnSurface);
                visual.arcAEnd = HALF;
                visual.arcAColor = color(androidx.appcompat.R.attr.colorPrimary);
                visual.arcBStart = HALF;
                visual.arcBEnd = 1f;
                visual.arcBColor = color(com.google.android.material.R.attr.colorTertiary);
                break;
            case CONFLICT:
                visual.fillColor = color(R.attr.colorGitConflictContainer);
                visual.glyphColor = color(R.attr.colorGitConflict);
                visual.arcAEnd = 1f;
                visual.arcAColor = visual.glyphColor;
                visual.dashed = true;
                break;
            case RUNNING:
                visual.fillColor = color(com.google.android.material.R.attr.colorSecondaryContainer);
                visual.glyphColor = color(androidx.appcompat.R.attr.colorPrimary);
                if (indeterminate) {
                    visual.arcAStart = indeterminatePhase;
                    visual.arcAEnd = indeterminatePhase + INDETERMINATE_ARC;
                } else {
                    visual.arcAEnd = progress;
                }
                visual.arcAColor = visual.glyphColor;
                break;
            case DONE:
                visual.fillColor = color(R.attr.colorGitAddedContainer);
                visual.glyphColor = color(R.attr.colorGitAdded);
                visual.arcAEnd = 1f;
                visual.arcAColor = visual.glyphColor;
                break;
            case FAILED:
                visual.fillColor = color(com.google.android.material.R.attr.colorErrorContainer);
                visual.glyphColor = color(androidx.appcompat.R.attr.colorError);
                visual.arcAEnd = 1f;
                visual.arcAColor = visual.glyphColor;
                break;
            case CLEAN:
            default:
                visual.fillColor = color(com.google.android.material.R.attr.colorSecondaryContainer);
                visual.glyphColor = color(com.google.android.material.R.attr.colorSecondary);
                visual.arcAEnd = 1f;
                visual.arcAColor = visual.glyphColor;
                break;
        }
        if (visual.arcBEnd <= visual.arcBStart) {
            // Zweiter Bogen unbenutzt: unsichtbar, damit Übergänge nicht aufblitzen.
            visual.arcBColor = ColorUtils.setAlphaComponent(visual.arcAColor, 0);
        }
        return visual;
    }

    @DrawableRes
    private static int glyphFor(
            @NonNull final Status forStatus
    ) {
        switch (forStatus) {
            case CHANGED:
                return R.drawable.ic_edit;
            case AHEAD:
                return R.drawable.ic_arrow_upward;
            case BEHIND:
                return R.drawable.ic_arrow_downward;
            case DIVERGED:
                return R.drawable.ic_swap_vert;
            case CONFLICT:
                return R.drawable.ic_warning;
            case RUNNING:
                return R.drawable.ic_sync;
            case DONE:
            case CLEAN:
                return R.drawable.ic_check;
            case FAILED:
                return R.drawable.ic_error;
            default:
                return R.drawable.ic_check;
        }
    }

    @ColorInt
    private int color(
            @AttrRes final int attribute
    ) {
        return MaterialColors.getColor(this, attribute);
    }

    private static void buildUnit(
            @NonNull final Path path
    ) {
        path.set(androidx.core.graphics.PathParser.createPathFromPathData(PEBBLE_PATH_DATA));
    }

    private static float lerp(
            final float start,
            final float end,
            final float fraction
    ) {
        return start + (end - start) * fraction;
    }

    /** Alles, was sich zwischen zwei Zuständen verändert und weich übergeblendet wird. */
    private static final class Visual {

        @ColorInt int fillColor;
        @ColorInt int trackColor;
        @ColorInt int glyphColor;
        float arcAStart;
        float arcAEnd;
        @ColorInt int arcAColor;
        float arcBStart;
        float arcBEnd;
        @ColorInt int arcBColor;
        boolean dashed;

        Visual copy() {
            final Visual copy = new Visual();
            copy.fillColor = fillColor;
            copy.trackColor = trackColor;
            copy.glyphColor = glyphColor;
            copy.arcAStart = arcAStart;
            copy.arcAEnd = arcAEnd;
            copy.arcAColor = arcAColor;
            copy.arcBStart = arcBStart;
            copy.arcBEnd = arcBEnd;
            copy.arcBColor = arcBColor;
            copy.dashed = dashed;
            return copy;
        }

        static Visual mix(
                final Visual start,
                final Visual end,
                final float fraction
        ) {
            final Visual mixed = new Visual();
            mixed.fillColor = ColorUtils.blendARGB(start.fillColor, end.fillColor, fraction);
            mixed.trackColor = ColorUtils.blendARGB(start.trackColor, end.trackColor, fraction);
            mixed.glyphColor = ColorUtils.blendARGB(start.glyphColor, end.glyphColor, fraction);
            mixed.arcAStart = lerp(start.arcAStart, end.arcAStart, fraction);
            mixed.arcAEnd = lerp(start.arcAEnd, end.arcAEnd, fraction);
            mixed.arcAColor = ColorUtils.blendARGB(start.arcAColor, end.arcAColor, fraction);
            mixed.arcBStart = lerp(start.arcBStart, end.arcBStart, fraction);
            mixed.arcBEnd = lerp(start.arcBEnd, end.arcBEnd, fraction);
            mixed.arcBColor = ColorUtils.blendARGB(start.arcBColor, end.arcBColor, fraction);
            mixed.dashed = fraction < 0.5f ? start.dashed : end.dashed;
            return mixed;
        }
    }
}
