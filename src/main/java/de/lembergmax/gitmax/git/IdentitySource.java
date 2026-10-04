package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.CommitIdentity;

import java.io.File;
import java.util.Optional;

/** Liefert die Commit-Identität, die für ein Repo gilt (Repo-Wahl, sonst Konto). */
@FunctionalInterface
public interface IdentitySource {

    /** Keine Identität bekannt: für Tests. */
    IdentitySource NONE = repo -> Optional.empty();

    @NonNull
    Optional<CommitIdentity> identityFor(
            @NonNull File repo
    );
}
