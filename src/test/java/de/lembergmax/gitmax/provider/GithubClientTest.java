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
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

public final class GithubClientTest {

    private static final String TOKEN = "ghp_geheimerTestToken123";

    private FakeServer server;
    private AccountEndpoint endpoint;
    private final GithubClient client = new GithubClient();

    @Before
    public void setUp() throws IOException {
        server = new FakeServer();
        endpoint = new AccountEndpoint(ProviderType.GITHUB, "github.example", server.url());
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void profileUsesPublicEmailAndReadsScopes() throws Exception {
        server.on("/user", FakeServer.Reply.ok(
                "{\"login\":\"max\",\"id\":42,\"name\":\"Max Lemberg\",\"email\":\"max@example.org\"}",
                Map.of("X-OAuth-Scopes", "repo, workflow")
        ));

        final ProviderProfile profile = client.fetchProfile(endpoint, TOKEN);

        assertEquals("max", profile.login());
        assertEquals(42L, profile.userId());
        assertEquals("Max Lemberg", profile.name());
        assertEquals("max@example.org", profile.email());
        assertEquals(List.of("repo", "workflow"), profile.scopes());
    }

    @Test
    public void profileFallsBackToNoReplyEmailAndLoginAsName() throws Exception {
        server.on("/user", FakeServer.Reply.ok("{\"login\":\"max\",\"id\":42,\"name\":null,\"email\":null}"));

        final ProviderProfile profile = client.fetchProfile(endpoint, TOKEN);

        assertEquals("max", profile.name());
        assertEquals("42+max@users.noreply.github.com", profile.email());
        assertTrue(profile.scopes().isEmpty());
    }

    @Test
    public void sendsBearerTokenAndGithubHeaders() throws Exception {
        server.on("/user", FakeServer.Reply.ok("{\"login\":\"max\",\"id\":1}"));

        client.fetchProfile(endpoint, TOKEN);

        final FakeServer.Recorded request = server.requests().get(0);
        assertEquals("Bearer " + TOKEN, request.header("Authorization"));
        assertEquals("application/vnd.github+json", request.header("Accept"));
        assertEquals("GitMax", request.header("User-Agent"));
    }

    @Test
    public void rejectedTokenIsUnauthorizedAndNeverLeaksTheToken() {
        server.on("/user", FakeServer.Reply.status(401, "{\"message\":\"Bad credentials " + TOKEN + "\"}"));

        final ProviderException failure = assertThrows(
                ProviderException.class,
                () -> client.fetchProfile(endpoint, TOKEN)
        );

        assertEquals(ProviderException.Kind.UNAUTHORIZED, failure.kind());
        assertFalse(failure.getMessage().contains(TOKEN));
    }

    @Test
    public void forbiddenWithoutRateLimitHeadersIsForbidden() {
        server.on("/user", FakeServer.Reply.status(403, "{\"message\":\"Resource not accessible\"}"));

        final ProviderException failure = assertThrows(
                ProviderException.class,
                () -> client.fetchProfile(endpoint, TOKEN)
        );

        assertEquals(ProviderException.Kind.FORBIDDEN, failure.kind());
    }

    @Test
    public void exhaustedQuotaIsRateLimitedWithWaitTimeFromReset() {
        final long reset = System.currentTimeMillis() / 1000 + 120;
        server.on("/user", FakeServer.Reply.status(
                403,
                "{\"message\":\"API rate limit exceeded\"}",
                Map.of("X-RateLimit-Remaining", "0", "X-RateLimit-Reset", String.valueOf(reset))
        ));

        final ProviderException failure = assertThrows(
                ProviderException.class,
                () -> client.fetchProfile(endpoint, TOKEN)
        );

        assertEquals(ProviderException.Kind.RATE_LIMITED, failure.kind());
        assertTrue("Wartezeit " + failure.retryAfterSeconds(), failure.retryAfterSeconds() > 100);
    }

    @Test
    public void tooManyRequestsUsesRetryAfterHeader() {
        server.on("/user", FakeServer.Reply.status(429, "{}", Map.of("Retry-After", "42")));

        final ProviderException failure = assertThrows(
                ProviderException.class,
                () -> client.fetchProfile(endpoint, TOKEN)
        );

        assertEquals(ProviderException.Kind.RATE_LIMITED, failure.kind());
        assertEquals(42L, failure.retryAfterSeconds());
    }

    @Test
    public void serverErrorIsReportedAsServer() {
        server.on("/user", FakeServer.Reply.status(502, "Bad Gateway"));

        final ProviderException failure = assertThrows(
                ProviderException.class,
                () -> client.fetchProfile(endpoint, TOKEN)
        );

        assertEquals(ProviderException.Kind.SERVER, failure.kind());
    }

    @Test
    public void unreadableBodyIsMalformed() {
        server.on("/user", FakeServer.Reply.ok("<html>kein JSON</html>"));

        final ProviderException failure = assertThrows(
                ProviderException.class,
                () -> client.fetchProfile(endpoint, TOKEN)
        );

        assertEquals(ProviderException.Kind.MALFORMED, failure.kind());
    }

    @Test
    public void unreachableHostIsReportedAsNetwork() {
        final AccountEndpoint closed = new AccountEndpoint(ProviderType.GITHUB, "x", "http://127.0.0.1:1");

        final ProviderException failure = assertThrows(
                ProviderException.class,
                () -> client.fetchProfile(closed, TOKEN)
        );

        assertEquals(ProviderException.Kind.NETWORK, failure.kind());
    }

    @Test
    public void followsTheLinkHeaderAcrossPages() throws Exception {
        server.on("/user/repos", FakeServer.Reply.ok(
                "[" + repoJson(1, "alpha", "max/alpha", false) + "]",
                Map.of("Link", "<" + server.url() + "/user/repos?page=2>; rel=\"next\", <" + server.url() + "/user/repos?page=9>; rel=\"last\"")
        ));
        server.on("/user/repos?page=2", FakeServer.Reply.ok("[" + repoJson(2, "beta", "org/beta", true) + "]"));

        final AtomicInteger progress = new AtomicInteger();
        final List<RemoteRepo> repos = client.listRepositories("acc", endpoint, TOKEN, new GitProviderClient.ListListener() {
            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public void onProgress(
                    final int loaded
            ) {
                progress.set(loaded);
            }
        });

        assertEquals(List.of("alpha", "beta"), repos.stream().map(RemoteRepo::name).toList());
        assertEquals(2, progress.get());
        assertEquals(2, server.requests().size());
        assertTrue(server.requests().get(0).pathAndQuery().contains("affiliation=owner,collaborator,organization_member"));
    }

    @Test
    public void mapsRepositoryFields() throws Exception {
        server.on("/user/repos", FakeServer.Reply.ok("["
                + "{\"id\":7,\"name\":\"alpha\",\"full_name\":\"max/alpha\",\"description\":\"Ein Test\","
                + "\"private\":true,\"archived\":true,\"fork\":true,\"default_branch\":\"main\","
                + "\"clone_url\":\"https://github.example/max/alpha.git\",\"ssh_url\":\"git@github.example:max/alpha.git\","
                + "\"html_url\":\"https://github.example/max/alpha\",\"pushed_at\":\"2026-10-03T12:00:00Z\"}"
                + "]"));

        final List<RemoteRepo> repos = client.listRepositories("acc", endpoint, TOKEN, GitProviderClient.ListListener.NONE);

        assertEquals(1, repos.size());
        final RemoteRepo repo = repos.get(0);
        assertEquals("acc", repo.accountId());
        assertEquals("7", repo.remoteId());
        assertEquals("alpha", repo.name());
        assertEquals("max/alpha", repo.fullPath());
        assertEquals("max", repo.owner());
        assertEquals("Ein Test", repo.description());
        assertTrue(repo.isPrivate());
        assertTrue(repo.isArchived());
        assertTrue(repo.isFork());
        assertEquals("main", repo.defaultBranch());
        assertEquals("https://github.example/max/alpha.git", repo.httpsUrl());
        assertEquals("git@github.example:max/alpha.git", repo.sshUrl());
        assertEquals("https://github.example/max/alpha", repo.webUrl());
        assertEquals(Instant.parse("2026-10-03T12:00:00Z").toEpochMilli(), repo.lastActivityMillis());
    }

    @Test
    public void missingOptionalFieldsBecomeEmptyValues() throws Exception {
        server.on("/user/repos", FakeServer.Reply.ok("["
                + "{\"id\":8,\"name\":\"leer\",\"full_name\":\"max/leer\",\"description\":null,\"default_branch\":null,"
                + "\"clone_url\":\"https://github.example/max/leer.git\"}"
                + "]"));

        final RemoteRepo repo = client.listRepositories("acc", endpoint, TOKEN, GitProviderClient.ListListener.NONE).get(0);

        assertEquals("", repo.description());
        assertEquals("", repo.defaultBranch());
        assertEquals(0L, repo.lastActivityMillis());
        assertFalse(repo.isPrivate());
    }

    @Test
    public void skipsEntriesWithoutCloneUrl() throws Exception {
        server.on("/user/repos", FakeServer.Reply.ok(
                "[{\"id\":1,\"name\":\"kaputt\",\"full_name\":\"max/kaputt\"}," + repoJson(2, "ok", "max/ok", false) + "]"));

        final List<RemoteRepo> repos = client.listRepositories("acc", endpoint, TOKEN, GitProviderClient.ListListener.NONE);

        assertEquals(List.of("ok"), repos.stream().map(RemoteRepo::name).toList());
    }

    @Test
    public void nextLinkOnAForeignHostIsRefusedSoTheTokenNeverLeavesTheHost() {
        server.on("/user/repos", FakeServer.Reply.ok(
                "[]",
                Map.of("Link", "<http://evil.invalid/user/repos?page=2>; rel=\"next\"")
        ));

        final ProviderException failure = assertThrows(
                ProviderException.class,
                () -> client.listRepositories("acc", endpoint, TOKEN, GitProviderClient.ListListener.NONE)
        );

        assertEquals(ProviderException.Kind.MALFORMED, failure.kind());
        assertEquals("nur die erste Seite wurde angefragt", 1, server.requests().size());
    }

    @Test
    public void cancellationStopsBeforeTheNextRequest() {
        server.on("/user/repos", FakeServer.Reply.ok("[]"));

        assertThrows(CancellationException.class, () -> client.listRepositories(
                "acc", endpoint, TOKEN,
                new GitProviderClient.ListListener() {
                    @Override
                    public boolean isCancelled() {
                        return true;
                    }

                    @Override
                    public void onProgress(
                            final int loaded
                    ) {
                        // nicht erreichbar: vor der ersten Anfrage abgebrochen
                    }
                }
        ));
        assertTrue(server.requests().isEmpty());
    }

    @Test
    public void createRepositoryPostsTheRequestAndReturnsTheNewRepo() throws Exception {
        server.on("/user/repos", FakeServer.Reply.status(201, repoJson(7, "neu", "max/neu", true)));

        final RemoteRepo created = client.createRepository("acc", endpoint, TOKEN, new NewRepo("neu", "Beschreibung ü", true));

        final FakeServer.Recorded request = server.requests().get(0);
        assertEquals("POST", request.method());
        assertEquals("Bearer " + TOKEN, request.header("Authorization"));
        assertTrue(request.header("Content-Type").startsWith("application/json"));
        final JSONObject body = new JSONObject(request.body());
        assertEquals("neu", body.getString("name"));
        assertEquals("Beschreibung ü", body.getString("description"));
        assertTrue(body.getBoolean("private"));
        assertFalse(body.getBoolean("auto_init"));
        assertEquals("max/neu", created.fullPath());
        assertEquals("https://github.example/max/neu.git", created.httpsUrl());
        assertEquals("acc", created.accountId());
    }

    @Test
    public void aTakenNameIsReportedAsTaken() {
        server.on("/user/repos", FakeServer.Reply.status(422,
                "{\"message\":\"Repository creation failed.\",\"errors\":[{\"message\":\"name already exists on this account\"}]}"));

        final ProviderException failure = assertThrows(ProviderException.class,
                () -> client.createRepository("acc", endpoint, TOKEN, new NewRepo("neu", "", true)));

        assertEquals(ProviderException.Kind.NAME_TAKEN, failure.kind());
    }

    @Test
    public void anotherValidationProblemIsReportedAsInvalid() {
        server.on("/user/repos", FakeServer.Reply.status(422, "{\"message\":\"Validation Failed\",\"errors\":[{\"message\":\"name is too long\"}]}"));

        final ProviderException failure = assertThrows(ProviderException.class,
                () -> client.createRepository("acc", endpoint, TOKEN, new NewRepo("neu", "", true)));

        assertEquals(ProviderException.Kind.INVALID, failure.kind());
    }

    @Test
    public void creatingWithoutTheRightIsForbiddenAndNeverLeaksTheToken() {
        server.on("/user/repos", FakeServer.Reply.status(403, "{\"message\":\"Resource not accessible by personal access token " + TOKEN + "\"}"));

        final ProviderException failure = assertThrows(ProviderException.class,
                () -> client.createRepository("acc", endpoint, TOKEN, new NewRepo("neu", "", true)));

        assertEquals(ProviderException.Kind.FORBIDDEN, failure.kind());
        assertFalse(failure.getMessage().contains(TOKEN));
    }

    @Test
    public void aRejectedTokenWhileCreatingIsUnauthorized() {
        server.on("/user/repos", FakeServer.Reply.status(401, "{\"message\":\"Bad credentials\"}"));

        final ProviderException failure = assertThrows(ProviderException.class,
                () -> client.createRepository("acc", endpoint, TOKEN, new NewRepo("neu", "", false)));

        assertEquals(ProviderException.Kind.UNAUTHORIZED, failure.kind());
    }

    @Test
    public void aCreateResponseWithoutCloneUrlIsMalformed() {
        server.on("/user/repos", FakeServer.Reply.status(201, "{\"id\":1}"));

        final ProviderException failure = assertThrows(ProviderException.class,
                () -> client.createRepository("acc", endpoint, TOKEN, new NewRepo("neu", "", false)));

        assertEquals(ProviderException.Kind.MALFORMED, failure.kind());
    }

    private static String repoJson(
            final int id,
            final String name,
            final String fullName,
            final boolean isPrivate
    ) {
        return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"full_name\":\"" + fullName + "\",\"private\":" + isPrivate
                + ",\"clone_url\":\"https://github.example/" + fullName + ".git\"}";
    }
}
