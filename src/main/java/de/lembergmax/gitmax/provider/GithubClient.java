package de.lembergmax.gitmax.provider;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.NoReplyEmail;
import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.NewRepo;
import de.lembergmax.gitmax.domain.model.RemoteRepo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub (github.com und GitHub Enterprise) über die REST-API.
 */
public final class GithubClient extends BaseProviderClient {

    private static final String API_VERSION = "2022-11-28";
    private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>\\s*;\\s*rel=\"next\"");

    @NonNull
    @Override
    public ProviderProfile fetchProfile(
            @NonNull final AccountEndpoint endpoint,
            @NonNull final String token
    ) throws ProviderException {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(token, "token");
        final String context = "GitHub /user";

        final Http.Response response = request(endpoint.apiBaseUrl() + "/user", headers(token), token, context);
        final JSONObject user = parseObject(response.body(), context);

        final String login = Json.string(user, "login");
        if (login == null) {
            throw new ProviderException(ProviderException.Kind.MALFORMED, context + ": login missing in the response", null);
        }
        final long id = user.optLong("id");
        final String publicEmail = Json.string(user, "email");

        return new ProviderProfile(
                login,
                id,
                Json.stringOr(user, "name", login),
                publicEmail != null ? publicEmail : NoReplyEmail.github(id, login),
                parseScopes(response)
        );
    }

    @NonNull
    @Override
    public List<RemoteRepo> listRepositories(
            @NonNull final String accountId,
            @NonNull final AccountEndpoint endpoint,
            @NonNull final String token,
            @NonNull final ListListener listener
    ) throws ProviderException {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(listener, "listener");
        final String context = "GitHub /user/repos";

        final List<RemoteRepo> repos = new ArrayList<>();
        String url = endpoint.apiBaseUrl() + "/user/repos?per_page=" + PAGE_SIZE
                + "&sort=updated&affiliation=owner,collaborator,organization_member";
        for (int page = 0; page < MAX_PAGES && url != null; page += 1) {
            throwIfCancelled(listener);
            final Http.Response response = request(url, headers(token), token, context);
            final JSONArray items = parseArray(response.body(), context);
            for (int index = 0; index < items.length(); index += 1) {
                final JSONObject item = items.optJSONObject(index);
                if (item != null) {
                    toRepo(accountId, item).ifPresent(repos::add);
                }
            }
            listener.onProgress(repos.size());
            url = nextUrl(response, endpoint, context, token);
        }
        return repos;
    }

    @NonNull
    @Override
    public RemoteRepo createRepository(
            @NonNull final String accountId,
            @NonNull final AccountEndpoint endpoint,
            @NonNull final String token,
            @NonNull final NewRepo request
    ) throws ProviderException {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(request, "request");
        final String context = "GitHub POST /user/repos";

        final JSONObject body = new JSONObject();
        try {
            body.put("name", request.name());
            body.put("description", request.description());
            body.put("private", request.isPrivate());
            body.put("auto_init", false);
        } catch (final JSONException impossible) {
            throw new IllegalStateException("Request cannot be serialized", impossible);
        }
        final Http.Response response = post(endpoint.apiBaseUrl() + "/user/repos", headers(token), body.toString(), token, context);
        if (!response.isSuccess()) {
            throw ProviderErrors.fromCreateResponse(response, context, token);
        }
        return toRepo(accountId, parseObject(response.body(), context)).orElseThrow(() ->
                new ProviderException(ProviderException.Kind.MALFORMED, context + ": response without clone URL", null));
    }

    private static Optional<RemoteRepo> toRepo(
            final String accountId,
            final JSONObject item
    ) {
        final String name = Json.string(item, "name");
        final String fullName = Json.string(item, "full_name");
        final String httpsUrl = Json.string(item, "clone_url");
        if (name == null || fullName == null || httpsUrl == null) {
            return Optional.empty();
        }
        final String pushedAt = Json.string(item, "pushed_at");
        final String activity = pushedAt != null ? pushedAt : Json.string(item, "updated_at");
        return Optional.of(new RemoteRepo(
                accountId,
                String.valueOf(item.optLong("id")),
                name,
                fullName,
                Json.stringOr(item, "description", ""),
                item.optBoolean("private"),
                item.optBoolean("archived"),
                item.optBoolean("fork"),
                Json.stringOr(item, "default_branch", ""),
                httpsUrl,
                Json.stringOr(item, "ssh_url", ""),
                Json.stringOr(item, "html_url", ""),
                parseMillis(activity)
        ));
    }

    /**
     * Adresse der nächsten Seite aus dem {@code Link}-Header. Zeigt sie auf einen anderen Host, wird
     * abgebrochen: der Token darf nie an einen fremden Host gehen.
     */
    private static String nextUrl(
            final Http.Response response,
            final AccountEndpoint endpoint,
            final String context,
            final String token
    ) throws ProviderException {
        final String link = response.header("Link").orElse("");
        final Matcher matcher = NEXT_LINK.matcher(link);
        if (!matcher.find()) {
            return null;
        }
        final String next = matcher.group(1);
        if (!ProviderErrors.sameOrigin(endpoint.apiBaseUrl(), next)) {
            throw new ProviderException(
                    ProviderException.Kind.MALFORMED,
                    context + ": next page is on a foreign host: " + ProviderErrors.redact(next, token),
                    null
            );
        }
        return next;
    }

    private static List<String> parseScopes(
            final Http.Response response
    ) {
        final List<String> scopes = new ArrayList<>();
        for (final String scope : response.header("X-OAuth-Scopes").orElse("").split(",")) {
            final String trimmed = scope.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                scopes.add(trimmed);
            }
        }
        return scopes;
    }

    private static Map<String, String> headers(
            final String token
    ) {
        return Map.of(
                "Authorization", "Bearer " + token,
                "Accept", "application/vnd.github+json",
                "X-GitHub-Api-Version", API_VERSION
        );
    }
}
