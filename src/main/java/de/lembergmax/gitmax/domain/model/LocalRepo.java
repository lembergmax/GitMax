package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Objects;

/**
 * Ein Git-Repo auf dem Gerät.
 *
 * @param directory Arbeitsverzeichnis (enthält {@code .git})
 * @param root      Arbeitsordner, unter dem es gefunden wurde
 */
public record LocalRepo(
        @NonNull File directory,
        @NonNull File root
) {

    public LocalRepo {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(root, "root");
    }

    /** Ordnername des Repos. */
    @NonNull
    public String name() {
        return directory.getName();
    }

    /**
     * Pfad relativ zum Arbeitsordner, mit Schrägstrichen ({@code gruppe/projekt}); für ein Repo, das
     * der Arbeitsordner selbst ist, dessen Name.
     */
    @NonNull
    public String relativePath() {
        final String rootPath = root.getAbsolutePath();
        final String path = directory.getAbsolutePath();
        if (path.equals(rootPath)) {
            return directory.getName();
        }
        final String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
        return path.startsWith(prefix)
                ? path.substring(prefix.length()).replace(File.separatorChar, '/')
                : path;
    }
}
