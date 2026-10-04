package de.lembergmax.gitmax.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;

/** Prüft, dass Weiterleitungen den Token-Header nie zu einem anderen Host mitnehmen. */
public final class HttpTest {

    private static final Map<String, String> AUTH = Map.of("Authorization", "Bearer geheim");

    @Test
    public void followsAGetRedirectOnTheSameHost() throws Exception {
        try (FakeServer server = new FakeServer()) {
            server.on("/alt", FakeServer.Reply.status(301, "", Map.of("Location", "/neu")));
            server.on("/neu", FakeServer.Reply.ok("angekommen"));

            final Http.Response response = Http.get(server.url() + "/alt", AUTH, 5_000);

            assertEquals(200, response.status());
            assertEquals("angekommen", response.body());
            assertEquals("Bearer geheim", server.requests().get(1).header("Authorization"));
        }
    }

    @Test
    public void doesNotFollowARedirectToAnotherHost() throws Exception {
        try (FakeServer server = new FakeServer(); FakeServer other = new FakeServer()) {
            server.on("/alt", FakeServer.Reply.status(302, "", Map.of("Location", other.url() + "/ziel")));
            other.on("/ziel", FakeServer.Reply.ok("fremd"));

            final Http.Response response = Http.get(server.url() + "/alt", AUTH, 5_000);

            // Gleicher Host (127.0.0.1) mit anderem Port gilt als anderer Ort.
            assertEquals(302, response.status());
            assertTrue(other.requests().isEmpty());
        }
    }

    @Test
    public void neverFollowsARedirectOfAPost() throws Exception {
        try (FakeServer server = new FakeServer()) {
            server.on("/anlegen", FakeServer.Reply.status(307, "", Map.of("Location", "/anderswo")));
            server.on("/anderswo", FakeServer.Reply.ok("zweiter Versuch"));

            final Http.Response response = Http.postJson(server.url() + "/anlegen", AUTH, "{}", 5_000);

            assertEquals(307, response.status());
            assertEquals(List.of("/anlegen"), server.requests().stream().map(FakeServer.Recorded::pathAndQuery).toList());
        }
    }

    @Test
    public void givesUpAfterAFewRedirects() throws Exception {
        try (FakeServer server = new FakeServer()) {
            server.on("/schleife", FakeServer.Reply.status(302, "", Map.of("Location", "/schleife")));

            final Http.Response response = Http.get(server.url() + "/schleife", AUTH, 5_000);

            assertEquals(302, response.status());
            assertEquals(4, server.requests().size());
        }
    }
}
