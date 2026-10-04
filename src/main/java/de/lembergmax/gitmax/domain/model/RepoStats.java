package de.lembergmax.gitmax.domain.model;

/**
 * Größe und Zustand der Git-Daten eines Repos.
 *
 * @param looseObjects  lose Objekte
 * @param packs         Pack-Dateien
 * @param looseBytes    Platz der losen Objekte
 * @param packBytes     Platz der Packs
 */
public record RepoStats(
        long looseObjects,
        long packs,
        long looseBytes,
        long packBytes
) {

    /** Gesamtgröße der Git-Daten. */
    public long totalBytes() {
        return looseBytes + packBytes;
    }
}
