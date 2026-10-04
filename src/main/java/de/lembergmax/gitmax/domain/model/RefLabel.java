package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Eine Marke an einem Commit: Branch, Remote-Branch, Tag oder der aktuelle HEAD.
 *
 * @param name kurzer Name wie {@code main}, {@code origin/main} oder {@code v1.0}
 * @param kind Art der Marke
 */
public record RefLabel(
        @NonNull String name,
        @NonNull Kind kind
) {

    /** Art einer Marke. */
    public enum Kind {
        HEAD,
        BRANCH,
        REMOTE_BRANCH,
        TAG
    }

    public RefLabel {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
    }
}
