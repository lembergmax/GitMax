package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

/**
 * Rückmeldung und Abbruch während eines Git-Vorgangs. Wird auf dem Arbeits-Thread aufgerufen.
 */
public interface GitProgress {

    /** Abschnitt eines Vorgangs; die Oberfläche macht daraus Text und Gesamtfortschritt. */
    enum Phase {
        CONNECTING,
        FETCHING,
        RECEIVING,
        RESOLVING,
        CHECKOUT,
        SUBMODULES,
        MERGING,
        PUSHING,
        WORKING
    }

    /** Wert von {@code fraction}, wenn sich der Fortschritt eines Abschnitts nicht beziffern lässt. */
    float UNKNOWN = -1f;

    /** Tut nichts und bricht nie ab. */
    GitProgress NONE = new GitProgress() {
        @Override
        public void onProgress(
                @NonNull final Phase phase,
                final float fraction
        ) {
            // bewusst leer: Aufrufer ohne Fortschrittsanzeige
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    /**
     * @param phase    aktueller Abschnitt
     * @param fraction Anteil 0 bis 1 innerhalb des Abschnitts oder {@link #UNKNOWN}
     */
    void onProgress(
            @NonNull Phase phase,
            float fraction
    );

    /** {@code true}, wenn der Nutzer abbrechen will; die Engine bricht dann so bald wie möglich ab. */
    boolean isCancelled();
}
