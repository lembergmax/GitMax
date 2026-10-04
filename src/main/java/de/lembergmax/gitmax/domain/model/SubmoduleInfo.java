package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Ein Submodul eines Repos.
 *
 * @param path        Pfad im Repo
 * @param url         Adresse laut {@code .gitmodules}
 * @param initialized das Submodul ist ausgecheckt
 * @param upToDate    der ausgecheckte Stand ist der vom Repo erwartete
 */
public record SubmoduleInfo(
        @NonNull String path,
        @NonNull String url,
        boolean initialized,
        boolean upToDate
) {

    public SubmoduleInfo {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(url, "url");
    }
}
