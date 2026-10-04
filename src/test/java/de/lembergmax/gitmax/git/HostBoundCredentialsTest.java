package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;

/** Prüft, dass das Token nur an den Host des Kontos geht, auch nach einer Weiterleitung. */
public final class HostBoundCredentialsTest {

    @Test
    public void giveLoginAndTokenToTheAccountsHost() throws Exception {
        final HostBoundCredentials credentials = new HostBoundCredentials("github.com", "max", "geheimes-token");
        final CredentialItem.Username user = new CredentialItem.Username();
        final CredentialItem.Password password = new CredentialItem.Password();

        final boolean given = credentials.get(new URIish("https://github.com/max/alpha.git"), user, password);

        assertTrue(given);
        assertEquals("max", user.getValue());
        assertArrayEquals("geheimes-token".toCharArray(), password.getValue());
    }

    @Test
    public void theHostNameIsComparedWithoutRegardToCase() throws Exception {
        final HostBoundCredentials credentials = new HostBoundCredentials("git.example.org", "max", "t");

        assertTrue(credentials.get(new URIish("https://GIT.Example.ORG/team/app.git"),
                new CredentialItem.Username(), new CredentialItem.Password()));
    }

    @Test
    public void giveNothingToAnotherHost() throws Exception {
        final HostBoundCredentials credentials = new HostBoundCredentials("github.com", "max", "geheimes-token");
        final CredentialItem.Username user = new CredentialItem.Username();
        final CredentialItem.Password password = new CredentialItem.Password();

        final boolean given = credentials.get(new URIish("https://evil.example/max/alpha.git"), user, password);

        assertFalse(given);
        assertNull(user.getValue());
        assertNull(password.getValue());
    }

    @Test
    public void aSubdomainIsAnotherHost() throws Exception {
        final HostBoundCredentials credentials = new HostBoundCredentials("github.com", "max", "t");

        assertFalse(credentials.get(new URIish("https://github.com.evil.example/max/alpha.git"),
                new CredentialItem.Username(), new CredentialItem.Password()));
        assertFalse(credentials.get(new URIish("https://api.github.com/max/alpha.git"),
                new CredentialItem.Username(), new CredentialItem.Password()));
    }

    @Test
    public void giveNothingWithoutAHost() throws Exception {
        final HostBoundCredentials credentials = new HostBoundCredentials("github.com", "max", "t");

        assertFalse(credentials.get(new URIish("/lokaler/pfad"), new CredentialItem.Username(), new CredentialItem.Password()));
        assertFalse(credentials.get(null, new CredentialItem.Username(), new CredentialItem.Password()));
    }
}
