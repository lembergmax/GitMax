package de.lembergmax.gitmax.domain;

import static org.junit.Assert.assertEquals;

import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.ProviderType;

import org.junit.Test;

public final class TokenPageTest {

    @Test
    public void githubOpensTheClassicTokenFormWithRepoAndWorkflow() {
        assertEquals(
                "https://github.com/settings/tokens/new?scopes=repo,workflow&description=GitMax",
                TokenPage.url(endpoint(ProviderType.GITHUB, ""))
        );
    }

    @Test
    public void githubEnterpriseUsesItsOwnHost() {
        assertEquals(
                "https://git.firma.example/settings/tokens/new?scopes=repo,workflow&description=GitMax",
                TokenPage.url(endpoint(ProviderType.GITHUB, "git.firma.example"))
        );
    }

    @Test
    public void gitlabOpensThePersonalAccessTokenFormWithGitScopes() {
        assertEquals(
                "https://gitlab.com/-/user_settings/personal_access_tokens?name=GitMax&scopes=api,read_repository,write_repository",
                TokenPage.url(endpoint(ProviderType.GITLAB, ""))
        );
    }

    @Test
    public void selfHostedGitlabKeepsItsPort() {
        assertEquals(
                "https://gitlab.intern:8443/-/user_settings/personal_access_tokens?name=GitMax&scopes=api,read_repository,write_repository",
                TokenPage.url(endpoint(ProviderType.GITLAB, "gitlab.intern:8443"))
        );
    }

    @Test
    public void aServerConnectedOverHttpOpensItsTokenPageOverHttp() {
        assertEquals(
                "http://gitlab.firma.example/-/user_settings/personal_access_tokens?name=GitMax&scopes=api,read_repository,write_repository",
                TokenPage.url(endpoint(ProviderType.GITLAB, "http://gitlab.firma.example"))
        );
        assertEquals(
                "http://git.firma.example/settings/tokens/new?scopes=repo,workflow&description=GitMax",
                TokenPage.url(endpoint(ProviderType.GITHUB, "http://git.firma.example"))
        );
    }

    private static AccountEndpoint endpoint(
            final ProviderType provider,
            final String input
    ) {
        return AccountEndpoint.fromHostInput(provider, input).orElseThrow();
    }
}
