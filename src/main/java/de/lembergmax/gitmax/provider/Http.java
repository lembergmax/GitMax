package de.lembergmax.gitmax.provider;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Sehr schmaler HTTP-Helfer auf Basis von {@link HttpURLConnection}, bewusst ohne Fremdbibliothek
 * (wie in den Schwesterprojekten). Alle Methoden blockieren und gehören auf einen Hintergrund-Thread.
 *
 * <p>Geloggt werden nur Methode, Host, Pfad und Status, nie Query, Header oder Rumpf, damit weder
 * Token noch Inhalte im Logcat landen.</p>
 */
final class Http {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final String LOG_TAG = "GitMaxHttp";
    private static final String USER_AGENT = "GitMax";
    private static final int MAX_REDIRECTS = 3;

    private Http() {
    }

    /**
     * Antwort einer Anfrage, auch bei Fehlerstatus.
     *
     * @param status  HTTP-Status
     * @param body    Antwortrumpf (bei Fehlern der Fehlerrumpf), nie {@code null}
     * @param headers Antwort-Header, Namen ohne Beachtung der Groß-/Kleinschreibung
     */
    record Response(
            int status,
            @NonNull String body,
            @NonNull Map<String, List<String>> headers
    ) {

        boolean isSuccess() {
            return status >= 200 && status < 300;
        }

        /** Erster Wert eines Headers, ohne Beachtung der Groß-/Kleinschreibung des Namens. */
        @NonNull
        Optional<String> header(
                @NonNull final String name
        ) {
            final List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
        }
    }

    @NonNull
    static Response get(
            @NonNull final String url,
            @NonNull final Map<String, String> headers,
            final int readTimeoutMs
    ) throws IOException {
        return send("GET", url, headers, null, readTimeoutMs);
    }

    /** Schickt {@code jsonBody} als {@code application/json}. */
    @NonNull
    static Response postJson(
            @NonNull final String url,
            @NonNull final Map<String, String> headers,
            @NonNull final String jsonBody,
            final int readTimeoutMs
    ) throws IOException {
        return send("POST", url, headers, jsonBody, readTimeoutMs);
    }

    /**
     * Schickt die Anfrage. Weiterleitungen verfolgt GitMax selbst und nur für {@code GET} innerhalb desselben Hosts: Die
     * Anmeldung steht im Header, und ob eine Plattform sie bei einem Hostwechsel selbst entfernt, ist ihre Sache. Alles
     * andere kommt als 3xx-Antwort zurück und gilt dem Aufrufer als unerwartet.
     */
    @NonNull
    private static Response send(
            @NonNull final String method,
            @NonNull final String url,
            @NonNull final Map<String, String> headers,
            @Nullable final String jsonBody,
            final int readTimeoutMs
    ) throws IOException {
        String current = url;
        for (int hop = 0; ; hop += 1) {
            final Response response = sendOnce(method, current, headers, jsonBody, readTimeoutMs);
            final String next = hop < MAX_REDIRECTS ? sameHostRedirect(method, current, response) : null;
            if (next == null) {
                return response;
            }
            current = next;
        }
    }

    /** Das Ziel einer Weiterleitung, wenn sie ein {@code GET} betrifft und auf demselben Host (und Schema) bleibt. */
    @Nullable
    static String sameHostRedirect(
            @NonNull final String method,
            @NonNull final String url,
            @NonNull final Response response
    ) throws IOException {
        final int status = response.status();
        final boolean redirect = status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
        if (!"GET".equals(method) || !redirect) {
            return null;
        }
        final Optional<String> location = response.header("Location");
        if (location.isEmpty()) {
            return null;
        }
        final URL from = new URL(url);
        final URL to;
        try {
            to = new URL(from, location.get());
        } catch (final IOException malformed) {
            return null;
        }
        final boolean same = from.getProtocol().equals(to.getProtocol())
                && from.getHost().equalsIgnoreCase(to.getHost())
                && from.getPort() == to.getPort();
        return same ? to.toString() : null;
    }

    @NonNull
    private static Response sendOnce(
            @NonNull final String method,
            @NonNull final String url,
            @NonNull final Map<String, String> headers,
            @Nullable final String jsonBody,
            final int readTimeoutMs
    ) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(readTimeoutMs);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            for (final Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            if (jsonBody != null) {
                final byte[] payload = jsonBody.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(payload.length);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }
            }

            final int status = connection.getResponseCode();
            final InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            final String body = readAll(stream);
            if (status >= 400) {
                Log.w(LOG_TAG, method + " " + hostAndPath(url) + " -> HTTP " + status);
            }
            return new Response(status, body, copyHeaders(connection.getHeaderFields()));
        } finally {
            connection.disconnect();
        }
    }

    @NonNull
    private static Map<String, List<String>> copyHeaders(
            @Nullable final Map<String, List<String>> source
    ) {
        final Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (source != null) {
            for (final Map.Entry<String, List<String>> entry : source.entrySet()) {
                // Die Statuszeile steht unter dem Schlüssel null.
                if (entry.getKey() != null) {
                    copy.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
                }
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    @NonNull
    private static String hostAndPath(
            @NonNull final String url
    ) {
        try {
            final URL parsed = new URL(url);
            return parsed.getHost() + parsed.getPath();
        } catch (final IOException malformed) {
            return "?";
        }
    }

    @NonNull
    private static String readAll(
            @Nullable final InputStream stream
    ) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            final char[] chunk = new char[4096];
            final StringBuilder text = new StringBuilder();
            int read;
            while ((read = reader.read(chunk)) != -1) {
                text.append(chunk, 0, read);
            }
            return text.toString();
        }
    }
}
