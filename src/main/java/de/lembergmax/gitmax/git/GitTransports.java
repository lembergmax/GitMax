package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.RemoteUrl;

import org.eclipse.jgit.api.TransportConfigCallback;

/** Richtet die Verbindung zu einem Remote ein, soweit der Übertragungsweg mehr braucht als Zugangsdaten (SSH). */
public interface GitTransports {

    /** Keine Besonderheiten: HTTPS und lokale Remotes. */
    GitTransports NONE = url -> null;

    /**
     * @return Einstellung für die Verbindung zu dieser Adresse, {@code null} wenn nichts einzurichten ist
     * @throws GitFailureException wenn der Weg nicht benutzbar ist (z. B. SSH ohne Schlüssel)
     */
    @Nullable
    TransportConfigCallback forUrl(
            @NonNull RemoteUrl url
    ) throws GitFailureException;
}
