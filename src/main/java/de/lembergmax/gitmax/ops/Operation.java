package de.lembergmax.gitmax.ops;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.git.GitEngine;
import de.lembergmax.gitmax.git.GitProgress;

import java.io.File;

/** Ein Auftrag für die Warteschlange. Läuft blockierend auf einem Arbeits-Thread. */
public interface Operation {

    @NonNull
    OperationKind kind();

    /** Anzeigename des Repos. */
    @NonNull
    String title();

    /** Ordner des Repos; je Ordner läuft immer nur ein Vorgang. */
    @NonNull
    File directory();

    @NonNull
    OperationOutcome execute(
            @NonNull GitEngine engine,
            @NonNull GitProgress progress
    ) throws GitFailureException;
}
