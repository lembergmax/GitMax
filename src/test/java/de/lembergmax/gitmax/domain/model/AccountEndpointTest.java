package de.lembergmax.gitmax.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Optional;

public final class AccountEndpointTest {

    @Test
    public void emptyInputMeansThePublicHostOfTheProvider() {
        final AccountEndpoint github = endpoint(ProviderType.GITHUB, "");
        final AccountEndpoint gitlab = endpoint(ProviderType.GITLAB, null);

        assertEquals("github.com", github.host());
        assertEquals("https://api.github.com", github.apiBaseUrl());
        assertTrue(github.isPublicHost());
        assertEquals("gitlab.com", gitlab.host());
        assertEquals("https://gitlab.com/api/v4", gitlab.apiBaseUrl());
    }

    @Test
    public void githubEnterpriseUsesTheApiV3PathOnItsOwnHost() {
        final AccountEndpoint enterprise = endpoint(ProviderType.GITHUB, "git.firma.example");

        assertEquals("https://git.firma.example/api/v3", enterprise.apiBaseUrl());
        assertFalse(enterprise.isPublicHost());
    }

    @Test
    public void selfHostedGitlabKeepsAPort() {
        final AccountEndpoint selfHosted = endpoint(ProviderType.GITLAB, "gitlab.intern:8443");

        assertEquals("gitlab.intern:8443", selfHosted.host());
        assertEquals("gitlab.intern", selfHosted.hostname());
        assertEquals("https://gitlab.intern:8443/api/v4", selfHosted.apiBaseUrl());
    }

    @Test
    public void schemePathAndCaseAreDropped() {
        final AccountEndpoint selfHosted = endpoint(ProviderType.GITLAB, "  HTTPS://GitLab.Example.ORG/gruppe/projekt  ");

        assertEquals("gitlab.example.org", selfHosted.host());
    }

    @Test
    public void rejectsInputThatIsNotAPlainHost() {
        assertEquals(Optional.empty(), AccountEndpoint.fromHostInput(ProviderType.GITLAB, "user@gitlab.example.org"));
        assertEquals(Optional.empty(), AccountEndpoint.fromHostInput(ProviderType.GITLAB, "git lab.example"));
        assertEquals(Optional.empty(), AccountEndpoint.fromHostInput(ProviderType.GITLAB, "-bad.example"));
        assertEquals(Optional.empty(), AccountEndpoint.fromHostInput(ProviderType.GITLAB, "bad_host.example"));
        assertEquals(Optional.empty(), AccountEndpoint.fromHostInput(ProviderType.GITLAB, "host:notaport"));
        assertEquals(Optional.empty(), AccountEndpoint.fromHostInput(ProviderType.GITLAB, "https://"));
    }

    @Test
    public void plainHttpIsOnlyAcceptedForLocalHostsAndOnlyWhenAllowed() {
        final AccountEndpoint local = AccountEndpoint.fromHostInput(ProviderType.GITHUB, "http://10.0.2.2:8080", true).orElseThrow();

        assertEquals("10.0.2.2:8080", local.host());
        assertEquals("http://10.0.2.2:8080/api/v3", local.apiBaseUrl());

        assertEquals("https://10.0.2.2:8080/api/v3",
                AccountEndpoint.fromHostInput(ProviderType.GITHUB, "http://10.0.2.2:8080", false).orElseThrow().apiBaseUrl());
        assertEquals("https://git.firma.example/api/v3",
                AccountEndpoint.fromHostInput(ProviderType.GITHUB, "http://git.firma.example", true).orElseThrow().apiBaseUrl());
        assertEquals("http://localhost:3000/api/v4",
                AccountEndpoint.fromHostInput(ProviderType.GITLAB, "http://localhost:3000", true).orElseThrow().apiBaseUrl());
    }

    private static AccountEndpoint endpoint(
            final ProviderType provider,
            final String input
    ) {
        return AccountEndpoint.fromHostInput(provider, input).orElseThrow(() -> new AssertionError("ungültig: " + input));
    }
}
