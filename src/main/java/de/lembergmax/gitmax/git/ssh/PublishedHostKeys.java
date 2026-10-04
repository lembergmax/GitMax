package de.lembergmax.gitmax.git.ssh;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Die Fingerabdrücke, die GitHub und GitLab.com für ihre SSH-Server veröffentlichen (Stand der
 * Dokumentation der Anbieter, geprüft am 4. Oktober 2026). Ein Server, der einen davon vorlegt, ist beim
 * ersten Kontakt ohne Rückfrage vertrauenswürdig; ein anderer Schlüssel für diese Hosts löst eine Warnung aus.
 */
public final class PublishedHostKeys {

    private static final Map<String, Set<String>> KEYS = Map.of(
            "github.com", Set.of(
                    "SHA256:uNiVztksCsDhcc0u9e8BujQXVUpKZIDTMczCvj3tD2s",
                    "SHA256:p2QAMXNIC1TJYWeIOttrVc98/R1BUFWu3/LiyKgUfQM",
                    "SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU"),
            "gitlab.com", Set.of(
                    "SHA256:eUXGGm1YGsMAS7vkcx6JOJdOGHPem5gQp4taiCfCLB8",
                    "SHA256:ROQFvPThGrW4RuWLoL9tq9I9zJ42fK4XywyRtbOz/EQ",
                    "SHA256:HbW3g8zUjNSksFbqTiUWPWg2Bq1x8xdGUrliXFzSnUw"));

    private PublishedHostKeys() {
    }

    /** {@code true}, wenn der Anbieter für diesen Host Fingerabdrücke veröffentlicht. */
    public static boolean covers(
            @NonNull final String host
    ) {
        return KEYS.containsKey(host.toLowerCase(Locale.ROOT));
    }

    /** {@code true}, wenn der Fingerabdruck zu den veröffentlichten dieses Hosts gehört. */
    public static boolean matches(
            @NonNull final String host,
            @NonNull final String fingerprint
    ) {
        final Set<String> published = KEYS.get(host.toLowerCase(Locale.ROOT));
        return published != null && published.contains(fingerprint);
    }
}
