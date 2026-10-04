package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Ein Branch.
 *
 * @param name      Name ohne Präfix, bei Remote-Branches {@code origin/main}
 * @param remote    {@code true} für Remote-Branches
 * @param current   {@code true} für den ausgecheckten Branch
 * @param upstream  verfolgter Remote-Branch, {@code null} wenn keiner
 * @param ahead     lokale Commits, die dem Upstream fehlen
 * @param behind    Commits des Upstreams, die lokal fehlen
 * @param tipId     vollständige Kennung des Branch-Kopfes
 * @param tipMillis Zeitpunkt des Kopf-Commits
 */
public record BranchInfo(
        @NonNull String name,
        boolean remote,
        boolean current,
        @Nullable String upstream,
        int ahead,
        int behind,
        @NonNull String tipId,
        long tipMillis
) {

    public BranchInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tipId, "tipId");
    }
}
