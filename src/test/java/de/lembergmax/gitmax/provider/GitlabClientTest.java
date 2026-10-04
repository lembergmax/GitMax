package de.lembergmax.gitmax.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.NewRepo;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.domain.model.RemoteRepo;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class GitlabClientTest {

    private static final String TOKEN = "glpat-geheimerTestToken";

    private FakeServer server;
    private AccountEndpoint endpoint;
    private final GitlabClient client = new GitlabClient();

    @Before
    public void setUp() throws IOException {
        server = new FakeServer();
        endpoint = new AccountEndpoint(ProviderType.GITLAB, "gitlab.example.org", server.url());
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void profilePrefersCommitEmailAndReadsScopes() throws Exception {
        server.on("/user", FakeServer.Reply.ok("{\"id\":9,\"username\":\"max\",\"name\":\"Max\","
                + "\"commit_email\":\"commit@example.org\",\"public_email\":\"public@example.org\",\"email\":\"privat@example.org\"}"));
        server.on("/personal_access_tokens/self", FakeServer.Reply.ok("{\"scopes\":[\"api\",\"read_repository\"]}"));

        final ProviderProfile profile = client.fetchProfile(endpoint, TOKEN);

        assertEquals("max", profile.login());
        assertEquals(9L, profile.userId());
        assertEquals("commit@example.org", profile.email());
        assertEquals(List.of("api", "read_repository"), profile.scopes());
    }

    @Test
    public void profileNeverUsesThePrivatePrimaryEmail() throws Exception {
        server.on("/user", FakeServer.Reply.ok("{\"id\":9,\"username\":\"max\",\"name\":null,"
                + "\"commit_email\":null,\"public_email\":\"\",\"email\":\"privat@example.org\"}"));

        final ProviderProfile profile = client.fetchProfile(endpoint, TOKEN);

        assertEquals("max", profile.name());
        assertEquals("9-max@users.noreply.gitlab.example.org", profile.email());
        assertFalse(profile.email().contains("privat"));
    }

    @Test
    public void missingScopeEndpointDoesNotFailTheProfile() throws Exception {
        server.on("/user", FakeServer.Reply.ok("{\"id\":9,\"username\":\"max\"}"));

        final ProviderProfile profile = client.fetchProfile(endpoint, TOKEN);

        assertTrue(profile.scopes().isEmpty());
    }

    @Test
    public void sendsThePrivateTokenHeader() throws Exception {
        server.on("/user", FakeServer.Reply.ok("{\"id\":9,\"username\":\"max\"}"));

        client.fetchProfile(endpoint, TOKEN);

        assertEquals(TOKEN, server.requests().get(0).header("PRIVATE-TOKEN"));
        assertEquals(null, server.requests().get(0).header("Authorization"));
    }

    @Test
    public void rejectedTokenIsUnauthorizedAndNeverLeaksTheToken() {
        server.on("/user", FakeServer.Reply.status(401, "{\"message\":\"401 Unauthorized " + TOKEN + "\"}"));

        final ProviderException failure = assertThrows(
                ProviderException.class,
                () -> client.fetchProfile(endpoint, TOKEN)
        );

        assertEquals(ProviderException.Kind.UNAUTHORIZED, failure.kind());
        assertFalse(failure.getMessage().contains(TOKEN));
    }

    @Test
    public void tooManyRequestsUsesRetryAfterHeader() {
        server.on("/user", FakeServer.Reply.status(429, "{}", Map.of("Retry-After", "30")));

        final ProviderException failure = assertThrows(
                ProviderException.class,
                () -> client.fetchProfile(endpoint, TOKEN)
        );

        assertEquals(ProviderException.Kind.RATE_LIMITED, failure.kind());
        assertEquals(30L, failure.retryAfterSeconds());
    }

    @Test
    public void paginatesWithNextPageHeaderUntilItIsEmpty() throws Exception {
        server.on("/projects?membership=true&per_page=100&order_by=last_activity_at&sort=desc&page=1",
                FakeServer.Reply.ok("[" + projectJson(1, "alpha", "max/alpha", "private") + "]", Map.of("X-Next-Page", "2")));
        server.on("/projects?membership=true&per_page=100&order_by=last_activity_at&sort=desc&page=2",
                FakeServer.Reply.ok("[" + projectJson(2, "beta", "gruppe/unter/beta", "public") + "]", Map.of("X-Next-Page", "")));

        final List<RemoteRepo> repos = client.listRepositories("acc", endpoint, TOKEN, GitProviderClient.ListListener.NONE);

        assertEquals(List.of("alpha", "beta"), repos.stream().map(RemoteRepo::name).toList());
        assertEquals(2, server.requests().size());
        assertEquals("gruppe/unter", repos.get(1).owner());
    }

    @Test
    public void mapsProjectFields() throws Exception {
        server.on("/projects", FakeServer.Reply.ok("["
                + "{\"id\":5,\"name\":\"alpha\",\"path_with_namespace\":\"max/alpha\",\"description\":\"Beschreibung\","
                + "\"visibility\":\"internal\",\"archived\":true,\"forked_from_project\":{\"id\":1},\"default_branch\":\"main\","
                + "\"http_url_to_repo\":\"https://gitlab.example.org/max/alpha.git\",\"ssh_url_to_repo\":\"git@gitlab.example.org:max/alpha.git\","
                + "\"web_url\":\"https://gitlab.example.org/max/alpha\",\"last_activity_at\":\"2026-10-03T12:00:00.000Z\"}"
                + "]"));

        final RemoteRepo repo = client.listRepositories("acc", endpoint, TOKEN, GitProviderClient.ListListener.NONE).get(0);

        assertEquals("5", repo.remoteId());
        assertEquals("max/alpha", repo.fullPath());
        assertEquals("Beschreibung", repo.description());
        assertTrue("intern gilt als nicht öffentlich", repo.isPrivate());
        assertTrue(repo.isArchived());
        assertTrue(repo.isFork());
        assertEquals("main", repo.defaultBranch());
        assertEquals("git@gitlab.example.org:max/alpha.git", repo.sshUrl());
        assertEquals(Instant.parse("2026-10-03T12:00:00Z").toEpochMilli(), repo.lastActivityMillis());
    }

    @Test
    public void publicProjectWithoutForkInfoIsNeitherPrivateNorFork() throws Exception {
        server.on("/projects", FakeServer.Reply.ok("[" + projectJson(3, "pub", "max/pub", "public") + "]"));

        final RemoteRepo repo = client.listRepositories("acc", endpoint, TOKEN, GitProviderClient.ListListener.NONE).get(0);

        assertFalse(repo.isPrivate());
        assertFalse(repo.isFork());
        assertFalse(repo.isArchived());
    }

    private static String projectJson(
            final int id,
            final String name,
            final String fullPath,
            final String visibility
    ) {
        return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"path_with_namespace\":\"" + fullPath + "\",\"visibility\":\""
                + visibility + "\",\"http_url_to_repo\":\"https://gitlab.example.org/" + fullPath + ".git\"}";
    }

    @Test
    public void createRepositoryMapsVisibilityAndReturnsTheNewRepo() throws Exception {
        server.on("/projects", FakeServer.Reply.status(201,
                "{\"id\":9,\"name\":\"neu\",\"path_with_namespace\":\"max/neu\",\"visibility\":\"private\","
                        + "\"http_url_to_repo\":\"https://gitlab.example.org/max/neu.git\",\"ssh_url_to_repo\":\"git@gitlab.example.org:max/neu.git\"}"));

        final RemoteRepo created = client.createRepository("acc", endpoint, TOKEN, new NewRepo("neu", "Text", true));

        final FakeServer.Recorded request = server.requests().get(0);
        assertEquals("POST", request.method());
        assertEquals(TOKEN, request.header("PRIVATE-TOKEN"));
        final JSONObject body = new JSONObject(request.body());
        assertEquals("neu", body.getString("name"));
        assertEquals("private", body.getString("visibility"));
        assertFalse(body.getBoolean("initialize_with_readme"));
        assertEquals("max/neu", created.fullPath());
        assertTrue(created.isPrivate());
        assertEquals("git@gitlab.example.org:max/neu.git", created.sshUrl());
    }

    @Test
    public void aPublicProjectUsesPublicVisibility() throws Exception {
        server.on("/projects", FakeServer.Reply.status(201,
                "{\"id\":9,\"name\":\"neu\",\"path_with_namespace\":\"max/neu\",\"visibility\":\"public\","
                        + "\"http_url_to_repo\":\"https://gitlab.example.org/max/neu.git\"}"));

        client.createRepository("acc", endpoint, TOKEN, new NewRepo("neu", "", false));

        assertEquals("public", new JSONObject(server.requests().get(0).body()).getString("visibility"));
    }

    @Test
    public void aTakenProjectNameIsReportedAsTaken() {
        server.on("/projects", FakeServer.Reply.status(400,
                "{\"message\":{\"name\":[\"has already been taken\"],\"path\":[\"has already been taken\"]}}"));

        final ProviderException failure = assertThrows(ProviderException.class,
                () -> client.createRepository("acc", endpoint, TOKEN, new NewRepo("neu", "", true)));

        assertEquals(ProviderException.Kind.NAME_TAKEN, failure.kind());
    }

    @Test
    public void anInvalidProjectNameIsReportedAsInvalid() {
        server.on("/projects", FakeServer.Reply.status(400, "{\"message\":{\"path\":[\"can contain only letters, digits\"]}}"));

        final ProviderException failure = assertThrows(ProviderException.class,
                () -> client.createRepository("acc", endpoint, TOKEN, new NewRepo("n e u", "", true)));

        assertEquals(ProviderException.Kind.INVALID, failure.kind());
    }
}
