package de.lembergmax.gitmax.provider;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.ProviderType;

import java.util.Objects;

/**
 * Wählt den passenden {@link GitProviderClient} für einen Anbieter.
 */
public final class ProviderClients {

    private ProviderClients() {
    }

    @NonNull
    public static GitProviderClient forProvider(
            @NonNull final ProviderType provider
    ) {
        Objects.requireNonNull(provider, "provider");
        return provider == ProviderType.GITHUB ? new GithubClient() : new GitlabClient();
    }
}
