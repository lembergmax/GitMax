package de.lembergmax.gitmax.domain;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.ProviderType;

import java.util.Objects;

/**
 * Adresse der Seite, auf der man beim Anbieter einen Personal Access Token mit den nötigen Rechten
 * anlegt. Der Anbieter füllt Name und Rechte vor, soweit er das unterstützt.
 */
public final class TokenPage {

    private static final String TOKEN_NAME = "GitMax";

    private TokenPage() {
    }

    @NonNull
    public static String url(
            @NonNull final AccountEndpoint endpoint
    ) {
        Objects.requireNonNull(endpoint, "endpoint");
        if (endpoint.provider() == ProviderType.GITHUB) {
            return endpoint.webBaseUrl() + "/settings/tokens/new?scopes=repo,workflow&description=" + TOKEN_NAME;
        }
        return endpoint.webBaseUrl() + "/-/user_settings/personal_access_tokens?name=" + TOKEN_NAME
                + "&scopes=api,read_repository,write_repository";
    }
}
