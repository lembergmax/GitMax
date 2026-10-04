package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.RemoteUrl;

import java.io.File;
import java.util.Objects;

/**
 * Auftrag zum Klonen eines Repos.
 *
 * @param url        Quelle
 * @param target     Zielordner; darf nicht existieren oder muss leer sein
 * @param branch     zu klonender Branch, {@code null} für den Standard-Branch des Remotes
 * @param shallow    nur den neuesten Stand holen (Tiefe 1)
 * @param submodules Submodule rekursiv mitklonen
 */
public record CloneRequest(
        @NonNull RemoteUrl url,
        @NonNull File target,
        @Nullable String branch,
        boolean shallow,
        boolean submodules
) {

    public CloneRequest {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(target, "target");
    }
}
