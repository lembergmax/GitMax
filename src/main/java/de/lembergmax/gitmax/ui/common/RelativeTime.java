package de.lembergmax.gitmax.ui.common;

import android.content.Context;
import android.text.format.DateFormat;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.R;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Zeitangaben wie „vor 5 Min.“ in der Sprache der App. {@code DateUtils} richtet sich nach der
 * Gerätesprache und mischte dadurch englische Texte in die deutsche Oberfläche.
 */
public final class RelativeTime {

    private static final long MINUTE_MS = TimeUnit.MINUTES.toMillis(1);
    private static final long HOUR_MS = TimeUnit.HOURS.toMillis(1);
    private static final long DAY_MS = TimeUnit.DAYS.toMillis(1);
    private static final int DAYS_BEFORE_DATE = 7;

    private RelativeTime() {
    }

    /** Zeitpunkt relativ zu {@code now}; Zeitpunkte in der Zukunft (Uhrenabweichung) gelten als „gerade eben“. */
    @NonNull
    public static String format(
            @NonNull final Context context,
            final long millis,
            final long now
    ) {
        final long age = now - millis;
        if (age < MINUTE_MS) {
            return context.getString(R.string.time_just_now);
        }
        if (age < HOUR_MS) {
            return context.getString(R.string.time_minutes, (int) (age / MINUTE_MS));
        }
        if (age < DAY_MS) {
            return context.getString(R.string.time_hours, (int) (age / HOUR_MS));
        }
        final int days = (int) (age / DAY_MS);
        if (days == 1) {
            return context.getString(R.string.time_yesterday);
        }
        if (days < DAYS_BEFORE_DATE) {
            return context.getString(R.string.time_days, days);
        }
        return DateFormat.getMediumDateFormat(context).format(new Date(millis));
    }
}
