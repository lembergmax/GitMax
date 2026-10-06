package de.lembergmax.gitmax.domain;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.AccountEndpoint;

import java.util.Collection;
import java.util.Objects;

/**
 * Wann eine Git-Adresse unverschlüsselt (HTTP) angesprochen werden darf. Die Plattform lässt Klartext zu, weil die Adresse
 * eines selbst gehosteten Servers erst zur Laufzeit feststeht; die Grenze zieht die App: HTTP gilt nur für Hosts von Konten,
 * die der Nutzer ausdrücklich mit {@code http://} verknüpft hat. Für jeden anderen Host bleibt HTTPS (oder SSH) Pflicht.
 */
public final class TransportPolicy {

    private TransportPolicy() {
    }

    /**
     * {@code true}, wenn die Adresse über ihren Übertragungsweg angesprochen werden darf: HTTPS und SSH immer, HTTP nur zu
     * einem Host, dessen Konto unverschlüsselt verknüpft ist.
     */
    public static boolean allows(
            @NonNull final RemoteUrl url,
            @NonNull final Collection<AccountEndpoint> endpoints
    ) {
        Objects.requireNonNull(url, "url");
        return url.scheme().isEncrypted() || isInsecureHost(endpoints, url.host());
    }

    /** {@code true}, wenn ein Konto mit diesem Hostnamen (ohne Port) unverschlüsselt verknüpft ist. */
    public static boolean isInsecureHost(
            @NonNull final Collection<AccountEndpoint> endpoints,
            @NonNull final String hostname
    ) {
        Objects.requireNonNull(endpoints, "endpoints");
        Objects.requireNonNull(hostname, "hostname");
        return endpoints.stream()
                .anyMatch(endpoint -> endpoint.isInsecure() && endpoint.hostname().equalsIgnoreCase(hostname));
    }
}
