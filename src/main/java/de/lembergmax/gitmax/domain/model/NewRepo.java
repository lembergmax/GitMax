package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Ein Repo, das beim Anbieter neu angelegt werden soll.
 *
 * @param name        Name des Repos
 * @param description Beschreibung, darf leer sein
 * @param isPrivate   nicht öffentlich sichtbar
 */
public record NewRepo(
        @NonNull String name,
        @NonNull String description,
        boolean isPrivate
) {

    public NewRepo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
    }
}
