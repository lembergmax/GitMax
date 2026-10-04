package de.lembergmax.gitmax.git.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Prüft die Kennungen der SSH-Server und die veröffentlichten Fingerabdrücke. */
public final class HostIdsTest {

    @Test
    public void theDefaultPortIsLeftOut() {
        assertEquals("github.com", HostIds.of("GitHub.com", 22));
        assertEquals("github.com", HostIds.of("github.com", -1));
    }

    @Test
    public void anotherPortUsesTheKnownHostsForm() {
        assertEquals("[git.example.com]:2222", HostIds.of("git.example.com", 2222));
    }

    @Test
    public void addressesFromJGitAreNormalized() {
        assertEquals("github.com", HostIds.parse("github.com"));
        assertEquals("github.com", HostIds.parse("github.com:22"));
        assertEquals("[10.0.2.2]:2222", HostIds.parse("10.0.2.2:2222"));
        assertEquals("[10.0.2.2]:2222", HostIds.parse("[10.0.2.2]:2222"));
        assertEquals("github.com", HostIds.parse("[github.com]:22"));
    }

    @Test
    public void theHostnameIsExtracted() {
        assertEquals("github.com", HostIds.hostname("github.com"));
        assertEquals("git.example.com", HostIds.hostname("[git.example.com]:2222"));
    }

    @Test
    public void publishedKeysCoverGithubAndGitlabOnly() {
        assertTrue(PublishedHostKeys.covers("github.com"));
        assertTrue(PublishedHostKeys.covers("GitLab.com"));
        assertFalse(PublishedHostKeys.covers("git.example.com"));
        assertTrue(PublishedHostKeys.matches("github.com", "SHA256:uNiVztksCsDhcc0u9e8BujQXVUpKZIDTMczCvj3tD2s"));
        assertFalse(PublishedHostKeys.matches("gitlab.com", "SHA256:uNiVztksCsDhcc0u9e8BujQXVUpKZIDTMczCvj3tD2s"));
    }
}
