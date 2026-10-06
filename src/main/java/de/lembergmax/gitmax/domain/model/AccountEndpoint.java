package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Wo ein Konto zu Hause ist: Anbieter, Web-Host (für Git-URLs und die Zuordnung von Repos zum Konto)
 * und Basis-URL der API.
 *
 * @param provider   Anbieter
 * @param host       Web-Host in Kleinbuchstaben, ggf. mit Port ({@code gitlab.example.org:8443})
 * @param apiBaseUrl Basis-URL der REST-API ohne abschließenden Schrägstrich
 */
public record AccountEndpoint(
        @NonNull ProviderType provider,
        @NonNull String host,
        @NonNull String apiBaseUrl
) {

    /** Gültiger Hostname mit optionalem Port; bewusst eng gefasst, damit keine URL-Bestandteile durchrutschen. */
    private static final Pattern HOST_PATTERN = Pattern.compile(
            "^[a-z0-9]([a-z0-9.-]*[a-z0-9])?(:[0-9]{1,5})?$"
    );

    public AccountEndpoint {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(apiBaseUrl, "apiBaseUrl");
    }

    /**
     * Baut den Endpunkt aus einer Nutzereingabe. Pfad und Schrägstriche werden verworfen. Die API wird über HTTPS
     * angesprochen; nur wenn die Eingabe ausdrücklich {@code http://} trägt und einen selbst gehosteten Server meint,
     * bleibt es bei HTTP (Server, die kein HTTPS anbieten). Die öffentlichen Hosts der Anbieter ({@code github.com},
     * {@code gitlab.com}) sprechen immer HTTPS, auch bei {@code http://}-Eingabe. Leere Eingabe ergibt den öffentlichen
     * Host des Anbieters.
     */
    @NonNull
    public static Optional<AccountEndpoint> fromHostInput(
            @NonNull final ProviderType provider,
            final String input
    ) {
        Objects.requireNonNull(provider, "provider");
        final String raw = input == null || input.isBlank() ? provider.defaultHost() : input;
        final String host = normalizeHost(raw);
        if (host == null || !HOST_PATTERN.matcher(host).matches()) {
            return Optional.empty();
        }
        final boolean insecure = raw.trim().toLowerCase(Locale.ROOT).startsWith("http://") && !isPublicProviderHost(host);
        return Optional.of(new AccountEndpoint(provider, host, apiBaseFor(provider, host, insecure ? "http" : "https")));
    }

    /** {@code true} für den öffentlichen Host des Anbieters (nicht selbst gehostet). */
    public boolean isPublicHost() {
        return host.equals(provider.defaultHost());
    }

    /**
     * {@code true}, wenn der Server unverschlüsselt (HTTP) angesprochen wird. Token und Code laufen dann im Klartext
     * durchs Netz; die App erlaubt das nur für selbst gehostete Server und nur nach ausdrücklicher Bestätigung.
     */
    public boolean isInsecure() {
        return apiBaseUrl.startsWith("http://");
    }

    /** Wurzel der Weboberfläche mit Schema, z. B. {@code https://gitlab.com} oder {@code http://git.firma.example}. */
    @NonNull
    public String webBaseUrl() {
        return (isInsecure() ? "http://" : "https://") + host;
    }

    /** Hostname ohne Port. */
    @NonNull
    public String hostname() {
        final int colon = host.indexOf(':');
        return colon < 0 ? host : host.substring(0, colon);
    }

    private static String apiBaseFor(
            final ProviderType provider,
            final String host,
            final String scheme
    ) {
        if (provider == ProviderType.GITHUB) {
            return host.equals(ProviderType.GITHUB.defaultHost())
                    ? "https://api.github.com"
                    : scheme + "://" + host + "/api/v3";
        }
        return scheme + "://" + host + "/api/v4";
    }

    private static boolean isPublicProviderHost(
            final String host
    ) {
        final int colon = host.indexOf(':');
        final String hostname = colon < 0 ? host : host.substring(0, colon);
        for (final ProviderType type : ProviderType.values()) {
            if (hostname.equals(type.defaultHost())) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeHost(
            final String raw
    ) {
        String host = raw.trim().toLowerCase(Locale.ROOT);
        final int scheme = host.indexOf("://");
        if (scheme >= 0) {
            host = host.substring(scheme + 3);
        }
        final int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        final int at = host.lastIndexOf('@');
        if (at >= 0) {
            return null;
        }
        return host.isEmpty() ? null : host;
    }
}
