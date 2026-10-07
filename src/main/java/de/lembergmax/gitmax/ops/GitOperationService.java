package de.lembergmax.gitmax.ops;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;

import de.lembergmax.gitmax.ServiceLocator;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Hält die App am Leben, solange Git-Vorgänge laufen: Vordergrunddienst vom Typ {@code dataSync} mit
 * Fortschritts-Benachrichtigung und Abbrechen-Aktion. Die Arbeit selbst macht die {@link OperationQueue};
 * der Dienst zeigt sie nur an, schützt den Prozess und beendet sich im Leerlauf.
 */
public final class GitOperationService extends Service {

    /** Bricht alle Vorgänge ab (Aktion der Benachrichtigung). */
    static final String ACTION_CANCEL_ALL = "de.lembergmax.gitmax.action.CANCEL_ALL";

    private static final long NOTIFICATION_INTERVAL_MS = 400L;
    private static final long WAKE_LOCK_LIMIT_MS = 6L * 60L * 60L * 1000L;
    private static final String WAKE_LOCK_TAG = "GitMax:operations";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private final OperationQueue.Listener listener = entries -> requestRefresh();
    private OperationQueue queue;
    private NotificationFactory notifications;
    private PowerManager.WakeLock wakeLock;
    private long sessionStart;
    private long lastNotification;
    private boolean finished;
    private boolean delayedRefreshScheduled;
    private int lastStartId;

    /**
     * Startet den Dienst. Aus dem Hintergrund verbietet Android das; dann laufen die Vorgänge ohne
     * Dienst weiter, solange der Prozess lebt.
     */
    static void start(
            @NonNull final Context context
    ) {
        try {
            ContextCompat.startForegroundService(context, new Intent(context, GitOperationService.class));
        } catch (final IllegalStateException notAllowedInBackground) {
            // Kein Vordergrunddienst möglich; die Warteschlange arbeitet trotzdem.
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        queue = ServiceLocator.from(this).operations().queue();
        notifications = new NotificationFactory(this);
        notifications.ensureChannels();
        sessionStart = System.currentTimeMillis();
        acquireWakeLock();
        queue.addListener(listener);
    }

    @Override
    public int onStartCommand(
            @Nullable final Intent intent,
            final int flags,
            final int startId
    ) {
        ServiceCompat.startForeground(this, NotificationFactory.ID_RUNNING, notifications.running(session()),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        // Ein neuer Start während des Abschlusses hält den Dienst am Leben: stop() beendet nur den zuletzt gestarteten.
        lastStartId = startId;
        finished = false;
        if (intent != null && ACTION_CANCEL_ALL.equals(intent.getAction())) {
            queue.cancelAll();
        }
        requestRefresh();
        return START_NOT_STICKY;
    }

    /** Android beendet {@code dataSync}-Dienste nach einem Zeitlimit; laufende Vorgänge werden abgebrochen. */
    @Override
    public void onTimeout(
            final int startId,
            final int fgsType
    ) {
        queue.cancelAll();
        notifyIfAllowed(NotificationFactory.ID_RESULT, notifications.timeout());
        finished = true;
        // Nach dem Zeitlimit muss der Dienst enden, auch wenn gerade ein neuer Start eingetroffen ist.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        queue.removeListener(listener);
        handler.removeCallbacksAndMessages(null);
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(
            final Intent intent
    ) {
        return null;
    }

    private void acquireWakeLock() {
        final PowerManager power = getSystemService(PowerManager.class);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
        wakeLock.acquire(WAKE_LOCK_LIMIT_MS);
    }

    /** Fasst Änderungen zusammen und verarbeitet sie auf dem Hauptthread. */
    private void requestRefresh() {
        if (refreshPending.compareAndSet(false, true)) {
            handler.post(this::refresh);
        }
    }

    private void refresh() {
        refreshPending.set(false);
        if (finished) {
            return;
        }
        if (!queue.hasActive()) {
            finishSession();
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - lastNotification >= NOTIFICATION_INTERVAL_MS) {
            lastNotification = now;
            notifyIfAllowed(NotificationFactory.ID_RUNNING, notifications.running(session()));
        } else if (!delayedRefreshScheduled) {
            // Der Fortschritt der letzten Millisekunden soll nicht verloren gehen.
            delayedRefreshScheduled = true;
            handler.postDelayed(() -> {
                delayedRefreshScheduled = false;
                requestRefresh();
            }, NOTIFICATION_INTERVAL_MS - (now - lastNotification));
        }
    }

    private void finishSession() {
        finished = true;
        final List<OperationEntry> session = session();
        int succeeded = 0;
        int failed = 0;
        int cancelled = 0;
        for (final OperationEntry entry : session) {
            switch (entry.state()) {
                case SUCCEEDED:
                    succeeded += 1;
                    break;
                case FAILED:
                    failed += 1;
                    break;
                case CANCELLED:
                    cancelled += 1;
                    break;
                default:
                    break;
            }
        }
        final boolean appVisible = ProcessLifecycleOwner.get().getLifecycle().getCurrentState()
                .isAtLeast(Lifecycle.State.STARTED);
        final boolean userCancelledEverything = succeeded == 0 && failed == 0;
        if (!session.isEmpty() && !appVisible && !userCancelledEverything) {
            notifyIfAllowed(NotificationFactory.ID_RESULT, notifications.result(succeeded, failed, cancelled));
        }
        stop();
    }

    /** Ohne Erlaubnis für Benachrichtigungen läuft der Dienst weiter, nur die Meldung entfällt. */
    private void notifyIfAllowed(
            final int id,
            final Notification notification
    ) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(id, notification);
        }
    }

    private void stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf(lastStartId);
    }

    /** Alle Vorgänge, die seit dem Start dieses Dienstes eingereiht wurden. */
    private List<OperationEntry> session() {
        return queue.snapshot().stream()
                .filter(entry -> entry.enqueuedAt() >= sessionStart)
                .collect(Collectors.toList());
    }
}
