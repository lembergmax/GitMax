package de.lembergmax.gitmax.ops;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ui.MainActivity;

import java.util.List;
import java.util.Objects;

/** Baut die Benachrichtigungen des Git-Dienstes: laufender Fortschritt, Ergebnis und Zeitüberschreitung. */
final class NotificationFactory {

    static final int ID_RUNNING = 1;
    static final int ID_RESULT = 2;

    private static final String CHANNEL_RUNNING = "git_running";
    private static final String CHANNEL_RESULTS = "git_results";
    private static final int PERCENT = 100;

    private final Context context;

    NotificationFactory(
            @NonNull final Context context
    ) {
        this.context = Objects.requireNonNull(context, "context");
    }

    /** Legt die Kanäle an; ein vorhandener Kanal bleibt unverändert. */
    void ensureChannels() {
        final NotificationManager manager = context.getSystemService(NotificationManager.class);
        final NotificationChannel running = new NotificationChannel(CHANNEL_RUNNING,
                context.getString(R.string.notification_channel_running), NotificationManager.IMPORTANCE_LOW);
        running.setDescription(context.getString(R.string.notification_channel_running_description));
        final NotificationChannel results = new NotificationChannel(CHANNEL_RESULTS,
                context.getString(R.string.notification_channel_results), NotificationManager.IMPORTANCE_DEFAULT);
        results.setDescription(context.getString(R.string.notification_channel_results_description));
        manager.createNotificationChannel(running);
        manager.createNotificationChannel(results);
    }

    /**
     * Fortschritt des laufenden Dienstes.
     *
     * @param session alle Vorgänge seit dem Start des Dienstes, älteste zuerst
     */
    @NonNull
    Notification running(
            @NonNull final List<OperationEntry> session
    ) {
        final int total = session.size();
        final long finished = session.stream().filter(entry -> entry.state().isFinished()).count();
        final OperationEntry current = currentOf(session);

        final NotificationCompat.Builder builder = base(CHANNEL_RUNNING)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(0, context.getString(R.string.notification_cancel), cancelIntent());
        if (current == null) {
            builder.setContentTitle(context.getString(R.string.app_name)).setProgress(0, 0, true);
            return builder.build();
        }
        builder.setContentTitle(context.getString(R.string.notification_running_title,
                OperationTexts.kind(context, current.kind()), current.title()));
        builder.setContentText(OperationTexts.status(context, current));
        if (total > 1) {
            builder.setSubText(context.getString(R.string.notification_running_count,
                    Math.min(total, (int) finished + 1), total));
        }
        final OperationEntry.Progress progress = current.progress();
        if (progress == null || progress.fraction() < 0f) {
            builder.setProgress(0, 0, true);
        } else {
            builder.setProgress(PERCENT, Math.round(Math.min(1f, progress.fraction()) * PERCENT), false);
        }
        return builder.build();
    }

    /** Ergebnis, wenn die App nicht im Vordergrund war. */
    @NonNull
    Notification result(
            final int succeeded,
            final int failed,
            final int cancelled
    ) {
        final String text;
        if (failed > 0) {
            text = context.getString(R.string.notification_done_mixed, succeeded, failed);
        } else if (succeeded > 0) {
            text = context.getString(R.string.notification_done_all_ok, succeeded);
        } else {
            text = context.getString(R.string.notification_done_cancelled, cancelled);
        }
        return base(CHANNEL_RESULTS)
                .setContentTitle(context.getString(R.string.notification_done_title))
                .setContentText(text)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build();
    }

    /** Android hat den Dienst nach seinem Zeitlimit beendet. */
    @NonNull
    Notification timeout() {
        return base(CHANNEL_RESULTS)
                .setContentTitle(context.getString(R.string.notification_timeout_title))
                .setContentText(context.getString(R.string.notification_timeout_body))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.notification_timeout_body)))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .build();
    }

    private NotificationCompat.Builder base(
            final String channel
    ) {
        return new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_sync)
                .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private PendingIntent cancelIntent() {
        final Intent intent = new Intent(context, GitOperationService.class)
                .setAction(GitOperationService.ACTION_CANCEL_ALL);
        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Der laufende Vorgang, sonst der nächste wartende. */
    private static OperationEntry currentOf(
            final List<OperationEntry> session
    ) {
        for (final OperationEntry entry : session) {
            if (entry.state() == OperationState.RUNNING) {
                return entry;
            }
        }
        for (final OperationEntry entry : session) {
            if (entry.state() == OperationState.QUEUED) {
                return entry;
            }
        }
        return null;
    }
}
