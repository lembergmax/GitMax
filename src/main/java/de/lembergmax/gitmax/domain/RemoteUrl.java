package de.lembergmax.gitmax.domain;

import androidx.annotation.NonNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Eine Git-Remote-Adresse in normalisierter Form. Zugangsdaten in der Adresse werden beim Parsen
 * verworfen: Token gehören in den {@code SecretVault}, nie in eine URL oder in die {@code .git/config}.
 *
 * <p>Unterstützt {@code https://…}, {@code http://…}, {@code ssh://…} und die scp-artige Kurzform
 * {@code git@host:besitzer/repo.git}.</p>
 *
 * @param scheme  Übertragungsweg der Originaladresse
 * @param host    Hostname in Kleinbuchstaben
 * @param port    Port der Originaladresse oder {@code -1}, wenn keiner angegeben war
 * @param sshUser Benutzername für SSH, bei HTTP(S) ohne Bedeutung
 * @param path    Pfad ohne führenden Schrägstrich und ohne {@code .git}, z. B. {@code besitzer/repo}
 */
public record RemoteUrl(
        @NonNull Scheme scheme,
        @NonNull String host,
        int port,
        @NonNull String sshUser,
        @NonNull String path
) {

    /** Übertragungsweg einer Remote-Adresse. */
    public enum Scheme {
        HTTPS,
        HTTP,
        SSH;

        /** {@code true}, wenn die Verbindung verschlüsselt ist. */
        public boolean isEncrypted() {
            return this != HTTP;
        }
    }

    private static final String DEFAULT_SSH_USER = "git";
    private static final String GIT_SUFFIX = ".git";
    private static final int NO_PORT = -1;

    /** scp-artige Kurzform: {@code [benutzer@]host:pfad}, ausdrücklich ohne {@code ://}. */
    private static final Pattern SCP_LIKE = Pattern.compile(
            "^(?:(?<user>[^@/\\s:]+)@)?(?<host>[^:/\\s@]+):(?!//)(?<path>[^\\s]+)$"
    );

    public RemoteUrl {
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(sshUser, "sshUser");
        Objects.requireNonNull(path, "path");
    }

    /**
     * Zerlegt eine Adresse. Liefert {@link Optional#empty()}, wenn sie kein erkennbares Repo
     * {@code besitzer/repo} bezeichnet oder ein nicht unterstütztes Schema hat.
     */
    @NonNull
    public static Optional<RemoteUrl> parse(
            final String raw
    ) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        final String trimmed = raw.trim();
        if (trimmed.contains("://")) {
            return parseUri(trimmed);
        }
        return parseScpLike(trimmed);
    }

    /** Name des Repos, also das letzte Pfadsegment. */
    @NonNull
    public String repoName() {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Besitzer bzw. Namespace, bei GitLab-Untergruppen mit Schrägstrichen ({@code gruppe/untergruppe}). */
    @NonNull
    public String owner() {
        return path.substring(0, path.lastIndexOf('/'));
    }

    /** Unmittelbares Elternsegment, geeignet für Ordnernamen ({@code untergruppe}). */
    @NonNull
    public String ownerSegment() {
        final String owner = owner();
        return owner.substring(owner.lastIndexOf('/') + 1);
    }

    /** Anzeigename {@code besitzer/repo}. */
    @NonNull
    public String displayName() {
        return path;
    }

    /** Dieselbe Adresse über HTTPS; ein SSH-Port wird nicht übernommen, er gilt nur für SSH. */
    @NonNull
    public String toHttpsUrl() {
        final boolean keepPort = scheme != Scheme.SSH && port != NO_PORT;
        return "https://" + host + (keepPort ? ":" + port : "") + "/" + path + GIT_SUFFIX;
    }

    /** Dieselbe Adresse über SSH, in der kurzen Form, solange kein eigener Port nötig ist. */
    @NonNull
    public String toSshUrl() {
        if (scheme == Scheme.SSH && port != NO_PORT) {
            return "ssh://" + sshUser + "@" + host + ":" + port + "/" + path + GIT_SUFFIX;
        }
        return sshUser + "@" + host + ":" + path + GIT_SUFFIX;
    }

    /** Die Adresse so, wie sie ohne Zugangsdaten in einer {@code .git/config} stehen darf. */
    @NonNull
    public String toCanonicalUrl() {
        return scheme == Scheme.SSH ? toSshUrl() : originalWebUrl();
    }

    /** Gleiches Repo auf demselben Host, unabhängig vom Übertragungsweg und von der Groß-/Kleinschreibung. */
    public boolean sameRepoAs(
            @NonNull final RemoteUrl other
    ) {
        Objects.requireNonNull(other, "other");
        return host.equalsIgnoreCase(other.host) && path.equalsIgnoreCase(other.path);
    }

    private String originalWebUrl() {
        final String schemeName = scheme == Scheme.HTTP ? "http" : "https";
        return schemeName + "://" + host + (port != NO_PORT ? ":" + port : "") + "/" + path + GIT_SUFFIX;
    }

    private static Optional<RemoteUrl> parseUri(
            final String text
    ) {
        final URI uri;
        try {
            uri = new URI(text);
        } catch (final URISyntaxException malformed) {
            return Optional.empty();
        }
        final String schemeName = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        final Scheme scheme;
        switch (schemeName) {
            case "https":
                scheme = Scheme.HTTPS;
                break;
            case "http":
                scheme = Scheme.HTTP;
                break;
            case "ssh":
                scheme = Scheme.SSH;
                break;
            default:
                return Optional.empty();
        }
        final String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        return normalizePath(uri.getPath()).map(path -> new RemoteUrl(
                scheme,
                host.toLowerCase(Locale.ROOT),
                uri.getPort(),
                scheme == Scheme.SSH ? userFrom(uri.getUserInfo()) : DEFAULT_SSH_USER,
                path
        ));
    }

    private static Optional<RemoteUrl> parseScpLike(
            final String text
    ) {
        final Matcher matcher = SCP_LIKE.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        final String user = matcher.group("user");
        return normalizePath(matcher.group("path")).map(path -> new RemoteUrl(
                Scheme.SSH,
                matcher.group("host").toLowerCase(Locale.ROOT),
                NO_PORT,
                user == null ? DEFAULT_SSH_USER : user,
                path
        ));
    }

    private static String userFrom(
            final String userInfo
    ) {
        if (userInfo == null || userInfo.isBlank()) {
            return DEFAULT_SSH_USER;
        }
        final int colon = userInfo.indexOf(':');
        return colon < 0 ? userInfo : userInfo.substring(0, colon);
    }

    /** Entfernt Schrägstriche an den Rändern und {@code .git}; verlangt mindestens {@code a/b} ohne Leersegmente. */
    private static Optional<String> normalizePath(
            final String rawPath
    ) {
        if (rawPath == null) {
            return Optional.empty();
        }
        String path = rawPath;
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.toLowerCase(Locale.ROOT).endsWith(GIT_SUFFIX)) {
            path = path.substring(0, path.length() - GIT_SUFFIX.length());
        }
        final String[] segments = path.split("/", -1);
        if (segments.length < 2) {
            return Optional.empty();
        }
        for (final String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return Optional.empty();
            }
        }
        return Optional.of(path);
    }
}
