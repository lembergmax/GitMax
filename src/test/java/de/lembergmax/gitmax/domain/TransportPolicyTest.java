package de.lembergmax.gitmax.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.ProviderType;

import org.junit.Test;

import java.util.List;

public final class TransportPolicyTest {

    private static final AccountEndpoint GITHUB = endpoint(ProviderType.GITHUB, "");
    private static final AccountEndpoint GITLAB_HTTP = endpoint(ProviderType.GITLAB, "http://gitlab.firma.example");
    private static final AccountEndpoint GITLAB_HTTPS = endpoint(ProviderType.GITLAB, "git.example.org");

    @Test
    public void encryptedAddressesAreAlwaysAllowed() {
        final List<AccountEndpoint> none = List.of();

        assertTrue(TransportPolicy.allows(url("https://github.com/max/alpha.git"), none));
        assertTrue(TransportPolicy.allows(url("git@github.com:max/alpha.git"), none));
        assertTrue(TransportPolicy.allows(url("ssh://git@gitlab.firma.example:2222/team/app.git"), List.of(GITLAB_HTTP)));
    }

    @Test
    public void plainHttpIsOnlyAllowedForAHostConnectedOverHttp() {
        final List<AccountEndpoint> accounts = List.of(GITHUB, GITLAB_HTTP, GITLAB_HTTPS);

        assertTrue(TransportPolicy.allows(url("http://gitlab.firma.example/team/app.git"), accounts));
        assertTrue(TransportPolicy.allows(url("http://GitLab.Firma.example:8080/team/app.git"), accounts));
        assertFalse("github.com ist verschlüsselt verknüpft", TransportPolicy.allows(url("http://github.com/max/alpha.git"), accounts));
        assertFalse("auch dieser Server ist verschlüsselt verknüpft", TransportPolicy.allows(url("http://git.example.org/a/b.git"), accounts));
        assertFalse("ein Host ohne Konto", TransportPolicy.allows(url("http://other.example/a/b.git"), accounts));
        assertFalse("ohne jedes Konto", TransportPolicy.allows(url("http://gitlab.firma.example/team/app.git"), List.of()));
    }

    @Test
    public void aSubdomainOfAnHttpHostIsAnotherHost() {
        assertFalse(TransportPolicy.allows(url("http://evil.gitlab.firma.example/team/app.git"), List.of(GITLAB_HTTP)));
        assertFalse(TransportPolicy.allows(url("http://gitlab.firma.example.evil.example/team/app.git"), List.of(GITLAB_HTTP)));
    }

    @Test
    public void isInsecureHostLooksAtTheHostnameWithoutPort() {
        final AccountEndpoint withPort = endpoint(ProviderType.GITLAB, "http://git.firma.example:8080");

        assertTrue(TransportPolicy.isInsecureHost(List.of(withPort), "git.firma.example"));
        assertFalse(TransportPolicy.isInsecureHost(List.of(withPort), "git.firma.example:8080"));
    }

    private static RemoteUrl url(
            final String raw
    ) {
        return RemoteUrl.parse(raw).orElseThrow(() -> new AssertionError("ungültig: " + raw));
    }

    private static AccountEndpoint endpoint(
            final ProviderType provider,
            final String input
    ) {
        return AccountEndpoint.fromHostInput(provider, input).orElseThrow();
    }
}
