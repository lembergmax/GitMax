package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.util.Objects;

/**
 * Gibt Login und Token nur an den Host heraus, zu dem das Konto gehört. JGits eigener Provider fragt den Host nicht:
 * Leitet ein Server auf einen fremden Host um und meldet dort „nicht angemeldet“, bekäme dieser Host sonst das Token.
 */
final class HostBoundCredentials extends UsernamePasswordCredentialsProvider {

    private final String host;

    HostBoundCredentials(
            @NonNull final String host,
            @NonNull final String login,
            @NonNull final String token
    ) {
        super(Objects.requireNonNull(login, "login"), Objects.requireNonNull(token, "token"));
        this.host = Objects.requireNonNull(host, "host");
    }

    @Override
    public boolean get(
            final URIish uri,
            final CredentialItem... items
    ) throws UnsupportedCredentialItem {
        if (uri == null || uri.getHost() == null || !host.equalsIgnoreCase(uri.getHost())) {
            return false;
        }
        return super.get(uri, items);
    }
}
