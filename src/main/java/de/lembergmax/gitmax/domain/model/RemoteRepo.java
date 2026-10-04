package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.RemoteUrl;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Repository, wie ein Anbieter es auflistet.
 *
 * @param accountId          Konto, über das es sichtbar ist
 * @param remoteId           Kennung beim Anbieter (nur innerhalb des Anbieters eindeutig)
 * @param name               Name des Repos
 * @param fullPath           vollständiger Pfad {@code besitzer/repo} bzw. {@code gruppe/untergruppe/repo}
 * @param description        Beschreibung, leer wenn keine vorhanden
 * @param isPrivate          nicht öffentlich sichtbar
 * @param isArchived         vom Besitzer archiviert (nur lesbar)
 * @param isFork             Abspaltung eines anderen Repos
 * @param defaultBranch      Standard-Branch, leer bei leerem Repo
 * @param httpsUrl           Klon-Adresse über HTTPS
 * @param sshUrl             Klon-Adresse über SSH
 * @param webUrl             Adresse der Webseite
 * @param lastActivityMillis letzte Aktivität in Millisekunden seit 1970, 0 wenn unbekannt
 */
public record RemoteRepo(
        @NonNull String accountId,
        @NonNull String remoteId,
        @NonNull String name,
        @NonNull String fullPath,
        @NonNull String description,
        boolean isPrivate,
        boolean isArchived,
        boolean isFork,
        @NonNull String defaultBranch,
        @NonNull String httpsUrl,
        @NonNull String sshUrl,
        @NonNull String webUrl,
        long lastActivityMillis
) {

    public RemoteRepo {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(remoteId, "remoteId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(fullPath, "fullPath");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(defaultBranch, "defaultBranch");
        Objects.requireNonNull(httpsUrl, "httpsUrl");
        Objects.requireNonNull(sshUrl, "sshUrl");
        Objects.requireNonNull(webUrl, "webUrl");
    }

    /** Die HTTPS-Adresse als {@link RemoteUrl}; leer, wenn der Anbieter keine brauchbare lieferte. */
    @NonNull
    public Optional<RemoteUrl> remoteUrl() {
        return RemoteUrl.parse(httpsUrl);
    }

    /** Besitzer bzw. Namespace, also alles vor dem letzten Pfadsegment (leer ohne Schrägstrich). */
    @NonNull
    public String owner() {
        final int slash = fullPath.lastIndexOf('/');
        return slash < 0 ? "" : fullPath.substring(0, slash);
    }
}
