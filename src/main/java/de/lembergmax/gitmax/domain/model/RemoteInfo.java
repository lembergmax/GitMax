package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Ein Remote des Repos.
 *
 * @param name Name wie {@code origin}
 * @param url  Adresse zum Abrufen und Pushen
 */
public record RemoteInfo(
        @NonNull String name,
        @NonNull String url
) {

    public RemoteInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(url, "url");
    }
}
