package de.lembergmax.gitmax.provider;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Übersetzt HTTP-Antworten und Netzwerkfehler in {@link ProviderException}. Zentral, damit GitHub
 * und GitLab gleich behandelt werden und kein Token in einer Meldung landet.
 */
final class ProviderErrors {

    private static final int MAX_MESSAGE_LENGTH = 200;

    private ProviderErrors() {
    }

    /** Fehlerausnahme zu einer Antwort mit Nicht-2xx-Status. */
    @NonNull
    static ProviderException fromResponse(
            @NonNull final Http.Response response,
            @NonNull final String context,
            @NonNull final String token
    ) {
        final int status = response.status();
        final String detail = describe(response, token);

        if (status == 401) {
            return new ProviderException(ProviderException.Kind.UNAUTHORIZED, context + ": token rejected" + detail, null);
        }
        if (status == 429 || (status == 403 && looksRateLimited(response))) {
            return new ProviderException(
                    ProviderException.Kind.RATE_LIMITED,
                    context + ": rate limit reached" + detail,
                    retryAfterSeconds(response),
                    null
            );
        }
        if (status == 403) {
            return new ProviderException(ProviderException.Kind.FORBIDDEN, context + ": permission missing" + detail, null);
        }
        if (status == 404) {
            return new ProviderException(ProviderException.Kind.NOT_FOUND, context + ": not found" + detail, null);
        }
        if (status >= 500) {
            return new ProviderException(ProviderException.Kind.SERVER, context + ": server error HTTP " + status + detail, null);
        }
        return new ProviderException(ProviderException.Kind.MALFORMED, context + ": unexpected response HTTP " + status + detail, null);
    }

    /**
     * Fehlerausnahme zu einem abgelehnten Anlegen: 400 und 422 heißen „Name vergeben“, wenn die Antwort davon spricht
     * ({@code already exists}, {@code already been taken}), sonst „Eingabe ungültig“; alles andere wie
     * {@link #fromResponse}.
     */
    @NonNull
    static ProviderException fromCreateResponse(
            @NonNull final Http.Response response,
            @NonNull final String context,
            @NonNull final String token
    ) {
        final int status = response.status();
        if (status == 400 || status == 422) {
            final String body = redact(response.body(), token);
            final String lower = body.toLowerCase(Locale.ROOT);
            final String shortBody = body.length() > MAX_MESSAGE_LENGTH ? body.substring(0, MAX_MESSAGE_LENGTH) + "…" : body;
            if (lower.contains("already exists") || lower.contains("already been taken")) {
                return new ProviderException(ProviderException.Kind.NAME_TAKEN, context + ": name already taken (" + shortBody + ")", null);
            }
            return new ProviderException(ProviderException.Kind.INVALID, context + ": input rejected (" + shortBody + ")", null);
        }
        return fromResponse(response, context, token);
    }

    /** Netzwerkfehler (keine Verbindung, Zeitüberschreitung, TLS). */
    @NonNull
    static ProviderException network(
            @NonNull final String context,
            @NonNull final Exception cause,
            @NonNull final String token
    ) {
        return new ProviderException(
                ProviderException.Kind.NETWORK,
                context + ": " + redact(String.valueOf(cause.getMessage()), token),
                cause
        );
    }

    /** Antwort ließ sich nicht als erwartetes JSON lesen. */
    @NonNull
    static ProviderException malformed(
            @NonNull final String context,
            @NonNull final JSONException cause
    ) {
        return new ProviderException(ProviderException.Kind.MALFORMED, context + ": response unreadable", cause);
    }

    /** Ersetzt jedes Vorkommen des Tokens durch {@code ***}. */
    @NonNull
    static String redact(
            @NonNull final String text,
            @NonNull final String token
    ) {
        Objects.requireNonNull(text, "text");
        return token.isBlank() ? text : text.replace(token, "***");
    }

    /** {@code true}, wenn die Antwort wie ein erreichtes Anfragelimit aussieht (statt eines fehlenden Rechts). */
    private static boolean looksRateLimited(
            final Http.Response response
    ) {
        if (response.header("Retry-After").isPresent()) {
            return true;
        }
        return response.header("X-RateLimit-Remaining").map(value -> value.trim().equals("0")).orElse(false);
    }

    /** Wartezeit aus {@code Retry-After} oder, wenn nicht vorhanden, aus {@code X-RateLimit-Reset} (Epoch-Sekunden). */
    private static long retryAfterSeconds(
            final Http.Response response
    ) {
        final Optional<Long> retryAfter = response.header("Retry-After").flatMap(ProviderErrors::parseLong);
        if (retryAfter.isPresent()) {
            return retryAfter.get();
        }
        return response.header("X-RateLimit-Reset")
                .flatMap(ProviderErrors::parseLong)
                .map(reset -> Math.max(0L, reset - Instant.now().getEpochSecond()))
                .orElse(0L);
    }

    private static Optional<Long> parseLong(
            final String value
    ) {
        try {
            return Optional.of(Long.parseLong(value.trim()));
        } catch (final NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /** Kurze Zusatzinfo aus dem Fehlerrumpf der API ({@code message}), ohne Token, auf 200 Zeichen gekürzt. */
    private static String describe(
            final Http.Response response,
            final String token
    ) {
        try {
            final String message = Json.string(new JSONObject(response.body()), "message");
            if (message == null) {
                return "";
            }
            final String safe = redact(message, token);
            return " (" + (safe.length() > MAX_MESSAGE_LENGTH ? safe.substring(0, MAX_MESSAGE_LENGTH) + "…" : safe) + ")";
        } catch (final JSONException notJson) {
            return "";
        }
    }

    /** Prüft, ob {@code next} auf demselben Host (Schema, Name, Port) liegt wie {@code base}. */
    static boolean sameOrigin(
            @NonNull final String base,
            @NonNull final String next
    ) {
        try {
            final URI baseUri = URI.create(base);
            final URI nextUri = URI.create(next);
            return Objects.equals(baseUri.getScheme(), nextUri.getScheme())
                    && Objects.equals(baseUri.getHost(), nextUri.getHost())
                    && baseUri.getPort() == nextUri.getPort();
        } catch (final IllegalArgumentException malformed) {
            return false;
        }
    }
}
