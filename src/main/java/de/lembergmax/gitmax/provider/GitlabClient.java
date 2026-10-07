package de.lembergmax.gitmax.provider;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.NoReplyEmail;
import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
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

/**
 * GitLab (gitlab.com und selbst gehostete Instanzen) über die REST-API v4.
 */
public final class GitlabClient extends BaseProviderClient {

    private static final String PUBLIC_VISIBILITY = "public";

    @NonNull
    @Override
    public ProviderProfile fetchProfile(
            @NonNull final AccountEndpoint endpoint,
            @NonNull final String token
    ) throws ProviderException {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(token, "token");
        final String context = "GitLab /user";

        final Http.Response response = request(endpoint.apiBaseUrl() + "/user", headers(token), token, context);
        final JSONObject user = parseObject(response.body(), context);

        final String login = Json.string(user, "username");
        if (login == null) {
            throw new ProviderException(ProviderException.Kind.MALFORMED, context + ": username missing in the response", null);
        }
        final long id = user.optLong("id");

        return new ProviderProfile(
                login,
                id,
                Json.stringOr(user, "name", login),
                chooseEmail(user, id, login, endpoint),
                fetchScopes(endpoint, token)
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
        final String context = "GitLab /projects";

        final String baseUrl = endpoint.apiBaseUrl() + "/projects?membership=true&per_page=" + PAGE_SIZE
                + "&order_by=last_activity_at&sort=desc";
        final List<RemoteRepo> repos = new ArrayList<>();
        String nextPage = "1";
        for (int page = 0; page < MAX_PAGES && nextPage != null; page += 1) {
            throwIfCancelled(listener);
            final Http.Response response = request(baseUrl + "&page=" + nextPage, headers(token), token, context);
            final JSONArray items = parseArray(response.body(), context);
            for (int index = 0; index < items.length(); index += 1) {
                final JSONObject item = items.optJSONObject(index);
                if (item != null) {
                    toRepo(accountId, endpoint, item).ifPresent(repos::add);
                }
            }
            listener.onProgress(repos.size());
            // Bei der letzten Seite ist der Header leer oder fehlt.
            nextPage = response.header("X-Next-Page").map(String::trim).filter(value -> !value.isEmpty()).orElse(null);
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
        final String context = "GitLab POST /projects";

        final JSONObject body = new JSONObject();
        try {
            body.put("name", request.name());
            body.put("path", request.name());
            body.put("description", request.description());
            body.put("visibility", request.isPrivate() ? "private" : PUBLIC_VISIBILITY);
            body.put("initialize_with_readme", false);
        } catch (final JSONException impossible) {
            throw new IllegalStateException("Request cannot be serialized", impossible);
        }
        final Http.Response response = post(endpoint.apiBaseUrl() + "/projects", headers(token), body.toString(), token, context);
        if (!response.isSuccess()) {
            throw ProviderErrors.fromCreateResponse(response, context, token);
        }
        return toRepo(accountId, endpoint, parseObject(response.body(), context)).orElseThrow(() ->
                new ProviderException(ProviderException.Kind.MALFORMED, context + ": response without clone URL", null));
    }

    /**
     * Commit-Adresse: die in GitLab hinterlegte Commit-Adresse, sonst die öffentliche, sonst die
     * No-Reply-Adresse. Die primäre (private) Kontoadresse wird bewusst nie übernommen.
     */
    private static String chooseEmail(
            final JSONObject user,
            final long id,
            final String login,
            final AccountEndpoint endpoint
    ) {
        for (final String key : new String[]{"commit_email", "public_email"}) {
            final String candidate = Json.string(user, key);
            if (CommitIdentity.isPlausibleEmail(candidate)) {
                return candidate;
            }
        }
        return NoReplyEmail.gitlab(id, login, endpoint.host());
    }

    /** Rechte des Tokens, soweit die Instanz sie verrät; Fehler (ältere Versionen, kein PAT) sind kein Problem. */
    private List<String> fetchScopes(
            final AccountEndpoint endpoint,
            final String token
    ) {
        try {
            final Http.Response response = request(
                    endpoint.apiBaseUrl() + "/personal_access_tokens/self",
                    headers(token),
                    token,
                    "GitLab /personal_access_tokens/self"
            );
            final JSONArray array = parseObject(response.body(), "GitLab token scopes").optJSONArray("scopes");
            final List<String> scopes = new ArrayList<>();
            if (array != null) {
                for (int index = 0; index < array.length(); index += 1) {
                    scopes.add(array.optString(index, "").trim().toLowerCase(Locale.ROOT));
                }
            }
            scopes.removeIf(String::isEmpty);
            return scopes;
        } catch (final ProviderException unavailable) {
            return List.of();
        }
    }

    private static Optional<RemoteRepo> toRepo(
            final String accountId,
            final AccountEndpoint endpoint,
            final JSONObject item
    ) {
        final String name = Json.string(item, "name");
        final String fullPath = Json.string(item, "path_with_namespace");
        final String httpsUrl = Json.string(item, "http_url_to_repo");
        if (name == null || fullPath == null || httpsUrl == null) {
            return Optional.empty();
        }
        return Optional.of(new RemoteRepo(
                accountId,
                String.valueOf(item.optLong("id")),
                name,
                fullPath,
                Json.stringOr(item, "description", ""),
                !PUBLIC_VISIBILITY.equals(Json.stringOr(item, "visibility", PUBLIC_VISIBILITY)),
                item.optBoolean("archived"),
                !item.isNull("forked_from_project") && item.has("forked_from_project"),
                Json.stringOr(item, "default_branch", ""),
                alignScheme(httpsUrl, endpoint),
                Json.stringOr(item, "ssh_url_to_repo", ""),
                Json.stringOr(item, "web_url", ""),
                parseMillis(Json.string(item, "last_activity_at"))
        ));
    }

    private static Map<String, String> headers(
            final String token
    ) {
        return Map.of(
                "PRIVATE-TOKEN", token,
                "Accept", "application/json"
        );
    }
}
