package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import org.eclipse.jgit.lib.ProgressMonitor;

import java.util.Locale;
import java.util.Objects;

/**
 * Übersetzt JGits {@link ProgressMonitor} in {@link GitProgress}: Aufgabentitel wie
 * „Receiving objects“ werden zu Abschnitten, die Fortschrittszahlen zu einem Anteil.
 */
final class ProgressAdapter implements ProgressMonitor {

    private final GitProgress progress;
    private GitProgress.Phase phase = GitProgress.Phase.WORKING;
    private int total;
    private int done;

    ProgressAdapter(
            @NonNull final GitProgress progress
    ) {
        this.progress = Objects.requireNonNull(progress, "progress");
    }

    @Override
    public void start(
            final int totalTasks
    ) {
        // Die Gesamtzahl der Aufgaben ist für die Anzeige nicht nötig.
    }

    @Override
    public void beginTask(
            final String title,
            final int totalWork
    ) {
        phase = phaseFor(title);
        total = totalWork;
        done = 0;
        progress.onProgress(phase, total > 0 ? 0f : GitProgress.UNKNOWN);
    }

    @Override
    public void update(
            final int completed
    ) {
        done += completed;
        progress.onProgress(phase, total > 0 ? Math.min(1f, (float) done / total) : GitProgress.UNKNOWN);
    }

    @Override
    public void endTask() {
        progress.onProgress(phase, 1f);
    }

    @Override
    public boolean isCancelled() {
        return progress.isCancelled();
    }

    @Override
    public void showDuration(
            final boolean enabled
    ) {
        // Dauern zeigt die Oberfläche selbst.
    }

    static GitProgress.Phase phaseFor(
            final String title
    ) {
        final String lower = title == null ? "" : title.toLowerCase(Locale.ROOT);
        // Meldungen des Servers („remote: Compressing objects“) gehören zum Abrufen, auch wenn sie „compressing“ enthalten.
        if (lower.startsWith("remote:") || lower.contains("receiving") || lower.contains("counting") || lower.contains("enumerating")) {
            return GitProgress.Phase.RECEIVING;
        }
        if (lower.contains("resolving")) {
            return GitProgress.Phase.RESOLVING;
        }
        if (lower.contains("checking out") || lower.contains("updating workdir")) {
            return GitProgress.Phase.CHECKOUT;
        }
        if (lower.contains("updating references") || lower.contains("finding sources")) {
            return GitProgress.Phase.FETCHING;
        }
        if (lower.contains("writing") || lower.contains("compressing")) {
            return GitProgress.Phase.PUSHING;
        }
        return GitProgress.Phase.WORKING;
    }
}
