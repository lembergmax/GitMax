package de.lembergmax.gitmax.ui.advanced;

import android.content.Context;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.ServiceLocator;

import java.io.File;

/** Erzeugt die {@link ListSource} zu einer Listenart. */
final class ListSources {

    /** Branch-Liste. */
    static final String BRANCHES = "branches";

    /** Stash-Liste. */
    static final String STASH = "stash";

    /** Tag-Liste. */
    static final String TAGS = "tags";

    /** Remote-Liste. */
    static final String REMOTES = "remotes";

    private ListSources() {
    }

    @NonNull
    static ListSource create(
            @NonNull final Context context,
            @NonNull final ServiceLocator services,
            @NonNull final String kind,
            @NonNull final File repo
    ) {
        switch (kind) {
            case BRANCHES:
                return new BranchSource(context, services, repo);
            case STASH:
                return new StashSource(context, services, repo);
            case TAGS:
                return new TagSource(context, services, repo);
            case REMOTES:
                return new RemoteSource(context, services, repo);
            default:
                throw new IllegalArgumentException("Unknown list kind: " + kind);
        }
    }
}
