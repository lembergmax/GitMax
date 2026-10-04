package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Objects;

/**
 * Auftrag zum Pushen des aktuellen Branches.
 *
 * @param repo  Arbeitsverzeichnis
 * @param tags  Tags mitschicken
 * @param force Überschreiben erzwingen; mit Prüfung, dass das Remote seit dem letzten Abruf unverändert ist
 */
public record PushRequest(
        @NonNull File repo,
        boolean tags,
        boolean force
) {

    public PushRequest {
        Objects.requireNonNull(repo, "repo");
    }
}
