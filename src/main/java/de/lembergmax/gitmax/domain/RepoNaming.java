package de.lembergmax.gitmax.domain;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.function.Function;

/**
 * Wählt den Ordnernamen eines Repos im Zielordner (flache Struktur {@code Ordner/Repo}).
 *
 * <p>Reihenfolge der Kandidaten: {@code repo}, dann {@code repo-besitzer}, dann {@code repo-2},
 * {@code repo-3}, … Ein Kandidat, in dem bereits dasselbe Repo liegt, wird wiederverwendet; ein
 * fremder Ordner wird übersprungen.</p>
 */
public final class RepoNaming {

    /** Was in einem Kandidaten-Ordner im Zielordner liegt. */
    public enum FolderState {
        /** Ordner existiert nicht. */
        MISSING,
        /** Ordner enthält bereits dasselbe Repo (gleiche Remote). */
        SAME_REPO,
        /** Ordner existiert, gehört aber zu etwas anderem. */
        OTHER
    }

    /**
     * Ergebnis der Namenswahl.
     *
     * @param folderName    gewählter Ordnername
     * @param alreadyCloned {@code true}, wenn dort schon dasselbe Repo liegt
     */
    public record Result(
            @NonNull String folderName,
            boolean alreadyCloned
    ) {

        public Result {
            Objects.requireNonNull(folderName, "folderName");
        }
    }

    private static final int MAX_NUMBERED_CANDIDATES = 99;
    private static final String FALLBACK_NAME = "repo";

    private RepoNaming() {
    }

    /**
     * @param url     Remote des zu klonenden Repos
     * @param inspect prüft, was im Zielordner unter einem Namen liegt
     */
    @NonNull
    public static Result resolve(
            @NonNull final RemoteUrl url,
            @NonNull final Function<String, FolderState> inspect
    ) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(inspect, "inspect");

        final String name = sanitize(url.repoName());
        final String owner = sanitize(url.ownerSegment());

        Result result = tryCandidate(name, inspect);
        if (result != null) {
            return result;
        }
        result = tryCandidate(name + "-" + owner, inspect);
        if (result != null) {
            return result;
        }
        for (int number = 2; number <= MAX_NUMBERED_CANDIDATES; number += 1) {
            result = tryCandidate(name + "-" + number, inspect);
            if (result != null) {
                return result;
            }
        }
        throw new IllegalStateException("No free folder name found for " + url.displayName());
    }

    /**
     * Macht aus einem Namen einen auf dem Telefonspeicher erlaubten Ordnernamen. Android verbietet
     * {@code : ? * " < > | \} und {@code /}; nur Punkte oder Leerraum ergäben keinen brauchbaren Namen.
     */
    @NonNull
    public static String sanitize(
            @NonNull final String rawName
    ) {
        Objects.requireNonNull(rawName, "rawName");
        final StringBuilder cleaned = new StringBuilder(rawName.length());
        for (int index = 0; index < rawName.length(); index += 1) {
            final char character = rawName.charAt(index);
            final boolean forbidden = "/\\:*?\"<>|".indexOf(character) >= 0 || Character.isISOControl(character);
            cleaned.append(forbidden ? '_' : character);
        }
        final String trimmed = cleaned.toString().strip();
        if (trimmed.isEmpty() || trimmed.chars().allMatch(character -> character == '.')) {
            return FALLBACK_NAME;
        }
        return trimmed;
    }

    private static Result tryCandidate(
            final String candidate,
            final Function<String, FolderState> inspect
    ) {
        final FolderState state = inspect.apply(candidate);
        if (state == FolderState.MISSING) {
            return new Result(candidate, false);
        }
        if (state == FolderState.SAME_REPO) {
            return new Result(candidate, true);
        }
        return null;
    }
}
