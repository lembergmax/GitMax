package de.lembergmax.gitmax.provider;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.AccountEndpoint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * Gemeinsamer Unterbau von {@link GithubClient} und {@link GitlabClient}: eine Anfrage absetzen, Fehler
 * einheitlich abbilden, JSON lesen.
 */
abstract class BaseProviderClient implements GitProviderClient {

    /** Höchstzahl Seiten je Auflistung, damit eine fehlerhafte Antwort nie endlos weiterblättert. */
    static final int MAX_PAGES = 100;
    static final int PAGE_SIZE = 100;
    private static final int READ_TIMEOUT_MS = 30_000;

    /** Setzt die Anfrage mit dem Token-Header des Anbieters ab. */
    @NonNull
    final Http.Response request(
            @NonNull final String url,
            @NonNull final Map<String, String> headers,
            @NonNull final String token,
            @NonNull final String context
    ) throws ProviderException {
        final Http.Response response;
        try {
            response = Http.get(url, headers, READ_TIMEOUT_MS);
        } catch (final IOException failure) {
            throw ProviderErrors.network(context, failure, token);
        }
        if (!response.isSuccess()) {
            throw ProviderErrors.fromResponse(response, context, token);
        }
        return response;
    }

    /**
     * Schickt eine JSON-Anfrage mit dem Token-Header ab. Statt der allgemeinen Fehlerabbildung bekommt der Aufrufer die
     * Antwort auch bei Fehlerstatus, weil ein Anlegen mit „Name vergeben“ oder „ungültig“ je Anbieter anders antwortet.
     */
    @NonNull
    final Http.Response post(
            @NonNull final String url,
            @NonNull final Map<String, String> headers,
            @NonNull final String jsonBody,
            @NonNull final String token,
            @NonNull final String context
    ) throws ProviderException {
        try {
            return Http.postJson(url, headers, jsonBody, READ_TIMEOUT_MS);
        } catch (final IOException failure) {
            throw ProviderErrors.network(context, failure, token);
        }
    }

    @NonNull
    static JSONObject parseObject(
            @NonNull final String body,
            @NonNull final String context
    ) throws ProviderException {
        try {
            return new JSONObject(body);
        } catch (final JSONException failure) {
            throw ProviderErrors.malformed(context, failure);
        }
    }

    @NonNull
    static JSONArray parseArray(
            @NonNull final String body,
            @NonNull final String context
    ) throws ProviderException {
        try {
            return new JSONArray(body);
        } catch (final JSONException failure) {
            throw ProviderErrors.malformed(context, failure);
        }
    }

    /** Wirft {@link CancellationException}, wenn der Aufrufer abgebrochen hat. */
    static void throwIfCancelled(
            @NonNull final ListListener listener
    ) {
        if (listener.isCancelled()) {
            throw new CancellationException("Listing cancelled");
        }
    }

    /**
     * Gleicht das Schema einer Klon-Adresse dem des Kontos an, wenn sie auf denselben Server zeigt. Ein GitLab hinter einem
     * TLS-Proxy meldet oft {@code http://}-Adressen (falsches {@code external_url}), ein reiner HTTP-Server {@code https://}-
     * Adressen: Mit der Adresse aus der API scheiterte der Klon dann an der Verbindung oder am Schutz vor Klartext.
     */
    @NonNull
    static String alignScheme(
            @NonNull final String cloneUrl,
            @NonNull final AccountEndpoint endpoint
    ) {
        final String wanted = endpoint.isInsecure() ? "http://" : "https://";
        final String other = endpoint.isInsecure() ? "https://" : "http://";
        if (!cloneUrl.regionMatches(true, 0, other, 0, other.length())) {
            return cloneUrl;
        }
        final String rest = cloneUrl.substring(other.length());
        final int slash = rest.indexOf('/');
        final String authority = slash < 0 ? rest : rest.substring(0, slash);
        final String path = slash < 0 ? "" : rest.substring(slash);
        final String defaultPort = other.equals("http://") ? ":80" : ":443";
        final String bareAuthority = authority.toLowerCase(Locale.ROOT).endsWith(defaultPort)
                ? authority.substring(0, authority.length() - defaultPort.length())
                : authority;
        return bareAuthority.equalsIgnoreCase(endpoint.host()) ? wanted + endpoint.host() + path : cloneUrl;
    }

    /** ISO-8601-Zeitstempel in Millisekunden seit 1970; 0 bei fehlendem oder unlesbarem Wert. */
    static long parseMillis(
            final String isoTimestamp
    ) {
        if (isoTimestamp == null) {
            return 0L;
        }
        try {
            return Instant.parse(isoTimestamp).toEpochMilli();
        } catch (final DateTimeParseException unreadable) {
            return 0L;
        }
    }
}
