package de.lembergmax.gitmax.domain;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Macht aus einem geteilten Text die Adresse eines Repos, das geklont werden soll. Das ist die einzige Stelle, an der
 * die App Text von einer fremden App entgegennimmt, deshalb gilt es streng: nur {@code https://}, keine Zugangsdaten in
 * der Adresse, begrenzte Länge, und bei Webseiten der bekannten Anbieter nur {@code besitzer/repo}. Geklont wird erst,
 * wenn der Nutzer im Klon-Sheet bestätigt.
 */
public final class ShareTarget {

    private static final int MAX_TEXT_LENGTH = 2048;
    private static final Pattern URL = Pattern.compile("https://[^\\s<>\"'`]+");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,;:!?)\\]]+$");

    /** Hosts, deren Webadressen {@code besitzer/repo} und danach Seitenpfade ({@code /tree/main}, {@code /issues/1}) tragen. */
    private static final Set<String> SEGMENT_HOSTS = Set.of("github.com");

    private ShareTarget() {
    }

    /**
     * @return die erste brauchbare Repo-Adresse im Text, sonst leer
     */
    @NonNull
    public static Optional<RemoteUrl> extract(
            final String text
    ) {
        if (text == null || text.isBlank() || text.length() > MAX_TEXT_LENGTH) {
            return Optional.empty();
        }
        final Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            final String candidate = TRAILING_PUNCTUATION.matcher(matcher.group()).replaceFirst("");
            final Optional<RemoteUrl> parsed = validate(candidate);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    private static Optional<RemoteUrl> validate(
            final String candidate
    ) {
        final int authorityStart = "https://".length();
        final int authorityEnd = firstOf(candidate, authorityStart, '/', '?', '#');
        final String authority = candidate.substring(authorityStart, authorityEnd);
        if (authority.contains("@")) {
            return Optional.empty();
        }
        final String withoutQuery = candidate.substring(0, firstOf(candidate, authorityEnd, '?', '#'));
        final Optional<RemoteUrl> parsed = RemoteUrl.parse(shorten(withoutQuery));
        if (parsed.isEmpty() || parsed.get().scheme() != RemoteUrl.Scheme.HTTPS) {
            return Optional.empty();
        }
        return parsed;
    }

    /** Bei GitHub-Webseiten bleiben nur Besitzer und Repo übrig. */
    private static String shorten(
            final String url
    ) {
        final String withoutScheme = url.substring("https://".length());
        final int slash = withoutScheme.indexOf('/');
        if (slash < 0) {
            return url;
        }
        final String host = withoutScheme.substring(0, slash).toLowerCase(Locale.ROOT);
        if (!SEGMENT_HOSTS.contains(host)) {
            return url;
        }
        final String[] segments = withoutScheme.substring(slash + 1).split("/");
        if (segments.length < 2) {
            return url;
        }
        return "https://" + host + "/" + segments[0] + "/" + segments[1];
    }

    private static int firstOf(
            final String text,
            final int from,
            final char... separators
    ) {
        int best = text.length();
        for (final char separator : separators) {
            final int index = text.indexOf(separator, from);
            if (index >= 0 && index < best) {
                best = index;
            }
        }
        return best;
    }
}
