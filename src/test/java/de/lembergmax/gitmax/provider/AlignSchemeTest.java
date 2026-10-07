package de.lembergmax.gitmax.provider;

import static org.junit.Assert.assertEquals;

import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.ProviderType;

import org.junit.Test;

public final class AlignSchemeTest {

    private static AccountEndpoint endpoint(
            final String input
    ) {
        return AccountEndpoint.fromHostInput(ProviderType.GITLAB, input).orElseThrow();
    }

    @Test
    public void anHttpOnlyServerGetsHttpCloneAddressesEvenWhenTheApiSaysHttps() {
        assertEquals("http://gitlab.firma.example/team/app.git",
                BaseProviderClient.alignScheme("https://gitlab.firma.example/team/app.git", endpoint("http://gitlab.firma.example")));
        assertEquals("http://gitlab.firma.example/team/app.git",
                BaseProviderClient.alignScheme("https://GitLab.Firma.example:443/team/app.git", endpoint("http://gitlab.firma.example")));
    }

    @Test
    public void anHttpsServerBehindAProxyGetsHttpsCloneAddressesEvenWhenTheApiSaysHttp() {
        assertEquals("https://gitlab.firma.example/team/app.git",
                BaseProviderClient.alignScheme("http://gitlab.firma.example/team/app.git", endpoint("gitlab.firma.example")));
        assertEquals("https://gitlab.firma.example/team/app.git",
                BaseProviderClient.alignScheme("http://gitlab.firma.example:80/team/app.git", endpoint("gitlab.firma.example")));
    }

    @Test
    public void anAddressOnAnotherServerStaysAsItIs() {
        assertEquals("http://other.example/team/app.git",
                BaseProviderClient.alignScheme("http://other.example/team/app.git", endpoint("gitlab.firma.example")));
        assertEquals("http://gitlab.firma.example:8080/team/app.git",
                BaseProviderClient.alignScheme("http://gitlab.firma.example:8080/team/app.git", endpoint("gitlab.firma.example")));
    }
}
