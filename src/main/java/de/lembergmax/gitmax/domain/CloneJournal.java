package de.lembergmax.gitmax.domain;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * Führt Buch über Klone, die gerade laufen. Stirbt die App mitten im Klon, bleibt ein halber Ordner
 * zurück; beim nächsten Start räumt die App alle noch verzeichneten Ordner auf.
 */
public interface CloneJournal {

    /** Führt kein Buch: für Tests. */
    CloneJournal NONE = new CloneJournal() {
        @Override
        public void begin(
                @NonNull final File target
        ) {
            // bewusst leer
        }

        @Override
        public void end(
                @NonNull final File target
        ) {
            // bewusst leer
        }
    };

    /** Ein Klon in diesen Ordner beginnt. */
    void begin(
            @NonNull File target
    );

    /** Der Klon ist beendet (erfolgreich, fehlgeschlagen oder bereinigt). */
    void end(
            @NonNull File target
    );
}
