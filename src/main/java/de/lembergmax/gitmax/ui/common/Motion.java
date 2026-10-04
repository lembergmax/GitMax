package de.lembergmax.gitmax.ui.common;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;

import com.google.android.material.transition.MaterialFadeThrough;
import com.google.android.material.transition.MaterialSharedAxis;
import com.google.android.material.transition.SlideDistanceProvider;

import de.lembergmax.gitmax.R;

import java.util.Objects;

/**
 * Zugriff auf die Motion-Tokens (Dauern in {@code integers.xml}, Interpolatoren in
 * {@code res/interpolator}). Alle Bewegung in der App holt Dauer und Easing von hier, damit gleiche
 * Aufgaben gleich schnell und gleich weich laufen.
 *
 * <p>Die Systemeinstellung „Animationen entfernen“ wirkt automatisch auf Animatoren (Dauer 0).
 * {@link #isReduced(Context)} dient dazu, Bewegung gezielt durch eine Überblendung zu ersetzen.</p>
 */
public final class Motion {

    private static final float ICON_SCALE_FROM = 0.25f;
    private static final float ICON_BLUR_DP = 4f;

    private Motion() {
    }

    /** Dauer eines Tokens in Millisekunden, z. B. {@code R.integer.motion_fast}. */
    public static int duration(
            @NonNull final Context context,
            @IntegerRes final int token
    ) {
        Objects.requireNonNull(context, "context");
        return context.getResources().getInteger(token);
    }

    /** Standard-Easing für Oberflächenbewegung. */
    @NonNull
    public static Interpolator smoothOut(
            @NonNull final Context context
    ) {
        return AnimationUtils.loadInterpolator(context, R.interpolator.smooth_out);
    }

    /** Easing für Icon- und Textwechsel. */
    @NonNull
    public static Interpolator easeInOut(
            @NonNull final Context context
    ) {
        return AnimationUtils.loadInterpolator(context, R.interpolator.ease_in_out);
    }

    /** {@code true}, wenn der Nutzer „Animationen entfernen“ aktiviert hat. */
    public static boolean isReduced(
            @NonNull final Context context
    ) {
        Objects.requireNonNull(context, "context");
        final float scale = Settings.Global.getFloat(
                context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
        );
        return scale == 0f;
    }

    /** Wechsel zwischen gleichrangigen Zielen (Navigationsleiste): symmetrisch, {@code fast}. */
    @NonNull
    public static MaterialFadeThrough fadeThrough(
            @NonNull final Context context
    ) {
        final MaterialFadeThrough transition = new MaterialFadeThrough();
        transition.setDuration(duration(context, R.integer.motion_fast));
        transition.setInterpolator(smoothOut(context));
        return transition;
    }

    /** Wechsel in die Tiefe (Liste → Detail) bzw. zurück: Verschiebung entlang einer Achse. */
    @NonNull
    public static MaterialSharedAxis sharedAxis(
            @NonNull final Context context,
            final int axis,
            final boolean forward
    ) {
        final MaterialSharedAxis transition = new MaterialSharedAxis(axis, forward);
        if (transition.getPrimaryAnimatorProvider() instanceof SlideDistanceProvider slide) {
            slide.setSlideDistance(context.getResources().getDimensionPixelSize(R.dimen.motion_distance_page));
        }
        transition.setDuration(duration(context, R.integer.motion_fast));
        transition.setInterpolator(smoothOut(context));
        return transition;
    }

    /**
     * Blendet ein Icon ein oder aus (Auswahl-Häkchen, Stage/Unstage): Skalierung 0,25 ↔ 1, Alpha 0 ↔ 1 und
     * Unschärfe 4 ↔ 0 dp. Symmetrisch, {@code fast}. Ohne {@code animate} oder bei „Animationen entfernen“
     * wird der Endzustand sofort gesetzt.
     */
    public static void swapIcon(
            @NonNull final View icon,
            final boolean visible,
            final boolean animate
    ) {
        Objects.requireNonNull(icon, "icon");
        final Context context = icon.getContext();
        icon.animate().cancel();
        final Object running = icon.getTag(R.id.tag_icon_blur);
        if (running instanceof Animator animator) {
            animator.cancel();
        }
        final float alpha = visible ? 1f : 0f;
        final float scale = visible ? 1f : ICON_SCALE_FROM;
        if (!animate || isReduced(context)) {
            icon.setAlpha(alpha);
            icon.setScaleX(scale);
            icon.setScaleY(scale);
            icon.setRenderEffect(null);
            return;
        }
        final int duration = duration(context, R.integer.motion_fast);
        final float blurPx = ICON_BLUR_DP * context.getResources().getDisplayMetrics().density;
        icon.animate()
                .alpha(alpha)
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(duration)
                .setInterpolator(easeInOut(context))
                .start();
        final ValueAnimator blur = visible ? ValueAnimator.ofFloat(blurPx, 0f) : ValueAnimator.ofFloat(0f, blurPx);
        blur.setDuration(duration);
        blur.setInterpolator(easeInOut(context));
        blur.addUpdateListener(animation -> {
            final float radius = (float) animation.getAnimatedValue();
            icon.setRenderEffect(radius < 0.5f ? null : RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        });
        icon.setTag(R.id.tag_icon_blur, blur);
        blur.start();
    }
}
