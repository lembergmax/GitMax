package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.RemoteUrl;

import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * Liefert zu einer Remote-Adresse die Zugangsdaten für HTTPS. Der Token steht nie in der Adresse.
 */
public interface GitCredentials {

    /** Keine Zugangsdaten: öffentliche Repos und lokale Remotes. */
    GitCredentials NONE = url -> null;

    /**
     * @return Zugangsdaten für den Host der Adresse, {@code null} wenn es kein passendes Konto gibt
     *         (dann wird anonym zugegriffen)
     * @throws GitFailureException wenn ein Konto passt, sein Token aber nicht lesbar ist
     */
    @Nullable
    CredentialsProvider forUrl(
            @NonNull RemoteUrl url
    ) throws GitFailureException;
}
