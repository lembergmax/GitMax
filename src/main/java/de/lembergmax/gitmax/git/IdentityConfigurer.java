package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.CommitIdentity;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Trägt Name und E-Mail der Commit-Identität in die Konfiguration eines Repos ein. Ohne sie schriebe JGit
 * bei Merges, Rebases, Cherry-picks und Stashes den Android-Benutzernamen als Autor in den Verlauf. Weicht
 * ein älterer Eintrag von der aktuell gültigen Identität ab, gilt die Identität der App: Wer sie in den
 * Repo-Einstellungen ändert, erwartet sie auch bei diesen Vorgängen.
 */
final class IdentityConfigurer {

    private static final String SECTION = "user";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";

    private final IdentitySource source;

    IdentityConfigurer(
            @NonNull final IdentitySource source
    ) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /** Gleicht {@code user.name} und {@code user.email} mit der gültigen Identität ab, sofern eine bekannt ist. */
    void ensure(
            @NonNull final Repository repository
    ) throws IOException {
        final Optional<CommitIdentity> identity = source.identityFor(repository.getWorkTree());
        if (identity.isEmpty()) {
            return;
        }
        final StoredConfig config = repository.getConfig();
        if (identity.get().name().equals(config.getString(SECTION, null, KEY_NAME))
                && identity.get().email().equals(config.getString(SECTION, null, KEY_EMAIL))) {
            return;
        }
        config.setString(SECTION, null, KEY_NAME, identity.get().name());
        config.setString(SECTION, null, KEY_EMAIL, identity.get().email());
        config.save();
    }
}
