package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.RemoteUrl;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Liest, woher ein lokales Repo stammt, ohne es zu öffnen: nur die {@code .git/config}. */
public final class RepoOrigins {

    private static final String SECTION = "remote";
    private static final String DEFAULT_REMOTE = "origin";
    private static final String KEY_URL = "url";

    private RepoOrigins() {
    }

    /**
     * Die Adresse des Remotes {@code origin}, sonst die des ersten Remotes.
     *
     * @return leer, wenn es kein Repo ist, kein Remote hat oder die Adresse nicht lesbar ist
     */
    @NonNull
    public static Optional<RemoteUrl> originOf(
            @NonNull final File repoDirectory
    ) {
        Objects.requireNonNull(repoDirectory, "repoDirectory");
        final File config = new File(repoDirectory, ".git/config");
        if (!config.isFile()) {
            return Optional.empty();
        }
        try {
            final FileBasedConfig loaded = new FileBasedConfig(config, FS.DETECTED);
            loaded.load();
            String url = loaded.getString(SECTION, DEFAULT_REMOTE, KEY_URL);
            if (url == null) {
                final Set<String> remotes = loaded.getSubsections(SECTION);
                url = remotes.stream()
                        .sorted()
                        .map(name -> loaded.getString(SECTION, name, KEY_URL))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            }
            return RemoteUrl.parse(url);
        } catch (final IOException | ConfigInvalidException unreadable) {
            return Optional.empty();
        }
    }
}
