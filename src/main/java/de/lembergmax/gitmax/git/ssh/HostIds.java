package de.lembergmax.gitmax.git.ssh;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Objects;

/** Kennung eines SSH-Servers: der Hostname, bei einem anderen Port als 22 {@code [host]:port} (wie in {@code known_hosts}). */
public final class HostIds {

    private static final int DEFAULT_PORT = 22;

    private HostIds() {
    }

    @NonNull
    public static String of(
            @NonNull final String host,
            final int port
    ) {
        final String name = Objects.requireNonNull(host, "host").toLowerCase(Locale.ROOT);
        return port <= 0 || port == DEFAULT_PORT ? name : "[" + name + "]:" + port;
    }

    /**
     * Zerlegt die Adresse, die JGit der Prüfung gibt ({@code host}, {@code host:port} oder {@code [host]:port}).
     */
    @NonNull
    public static String parse(
            @NonNull final String address
    ) {
        final String text = address.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("[")) {
            final int close = text.indexOf(']');
            if (close > 0) {
                final String host = text.substring(1, close);
                final int port = parsePort(text.substring(close + 1));
                return of(host, port);
            }
        }
        final int colon = text.lastIndexOf(':');
        if (colon > 0 && text.indexOf(':') == colon) {
            return of(text.substring(0, colon), parsePort(text.substring(colon)));
        }
        return text;
    }

    /** Der Hostname ohne Port. */
    @NonNull
    public static String hostname(
            @NonNull final String hostId
    ) {
        if (hostId.startsWith("[")) {
            final int close = hostId.indexOf(']');
            if (close > 0) {
                return hostId.substring(1, close);
            }
        }
        return hostId;
    }

    private static int parsePort(
            final String suffix
    ) {
        final String digits = suffix.startsWith(":") ? suffix.substring(1) : suffix;
        try {
            return Integer.parseInt(digits);
        } catch (final NumberFormatException none) {
            return DEFAULT_PORT;
        }
    }
}
