package de.lembergmax.gitmax.domain.model;

/**
 * Die unterstützten Git-Anbieter. Selbst gehostete GitLab-Instanzen und GitHub Enterprise sind
 * dieselben Typen mit einem anderen Host im {@link AccountEndpoint}.
 */
public enum ProviderType {

    GITHUB("GitHub", "github.com"),
    GITLAB("GitLab", "gitlab.com");

    private final String displayName;
    private final String defaultHost;

    ProviderType(
            final String displayName,
            final String defaultHost
    ) {
        this.displayName = displayName;
        this.defaultHost = defaultHost;
    }

    public String displayName() {
        return displayName;
    }

    /** Öffentlicher Host des Anbieters ({@code github.com}, {@code gitlab.com}). */
    public String defaultHost() {
        return defaultHost;
    }
}
