package de.lembergmax.gitmax.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Optional;

public final class RemoteUrlTest {

    @Test
    public void parsesHttpsUrlAndStripsGitSuffix() {
        final RemoteUrl url = parse("https://github.com/lembergmax/GitMax.git");

        assertEquals(RemoteUrl.Scheme.HTTPS, url.scheme());
        assertEquals("github.com", url.host());
        assertEquals("lembergmax/GitMax", url.path());
        assertEquals("GitMax", url.repoName());
        assertEquals("lembergmax", url.owner());
    }

    @Test
    public void dropsCredentialsFromHttpsUrl() {
        final RemoteUrl url = parse("https://max:ghp_secret@github.com/lembergmax/GitMax");

        assertEquals("https://github.com/lembergmax/GitMax.git", url.toCanonicalUrl());
        assertFalse(url.toCanonicalUrl().contains("ghp_secret"));
        assertFalse(url.toHttpsUrl().contains("@"));
    }

    @Test
    public void parsesScpLikeSshForm() {
        final RemoteUrl url = parse("git@github.com:lembergmax/GitMax.git");

        assertEquals(RemoteUrl.Scheme.SSH, url.scheme());
        assertEquals("git", url.sshUser());
        assertEquals("lembergmax/GitMax", url.path());
        assertEquals("git@github.com:lembergmax/GitMax.git", url.toSshUrl());
    }

    @Test
    public void parsesSshUrlWithPortAndKeepsPortForSsh() {
        final RemoteUrl url = parse("ssh://git@git.example.org:2222/team/tool.git");

        assertEquals(2222, url.port());
        assertEquals("ssh://git@git.example.org:2222/team/tool.git", url.toSshUrl());
    }

    @Test
    public void doesNotCarrySshPortOverToHttps() {
        final RemoteUrl url = parse("ssh://git@git.example.org:2222/team/tool.git");

        assertEquals("https://git.example.org/team/tool.git", url.toHttpsUrl());
    }

    @Test
    public void keepsHttpsPortWhenSwitchingToHttps() {
        final RemoteUrl url = parse("https://git.example.org:8443/team/tool.git");

        assertEquals("https://git.example.org:8443/team/tool.git", url.toHttpsUrl());
        assertEquals("git@git.example.org:team/tool.git", url.toSshUrl());
    }

    @Test
    public void supportsNestedGitlabGroups() {
        final RemoteUrl url = parse("https://gitlab.com/group/sub/project.git");

        assertEquals("group/sub", url.owner());
        assertEquals("sub", url.ownerSegment());
        assertEquals("project", url.repoName());
    }

    @Test
    public void lowercasesHostButKeepsPathCase() {
        final RemoteUrl url = parse("https://GitHub.COM/Owner/Repo");

        assertEquals("github.com", url.host());
        assertEquals("Owner/Repo", url.path());
    }

    @Test
    public void sameRepoIgnoresSchemeAndCase() {
        final RemoteUrl https = parse("https://github.com/Owner/Repo.git");
        final RemoteUrl ssh = parse("git@github.com:owner/repo.git");

        assertTrue(https.sameRepoAs(ssh));
    }

    @Test
    public void sameRepoRequiresSameHost() {
        final RemoteUrl github = parse("https://github.com/owner/repo.git");
        final RemoteUrl gitlab = parse("https://gitlab.com/owner/repo.git");

        assertFalse(github.sameRepoAs(gitlab));
    }

    @Test
    public void switchesFromSshToPlainHttpForAServerWithoutHttps() {
        final RemoteUrl url = parse("git@gitlab.firma.example:team/tool.git");

        assertEquals("http://gitlab.firma.example/team/tool.git", url.toWebUrl(false));
        assertEquals("https://gitlab.firma.example/team/tool.git", url.toWebUrl(true));
        assertEquals("http://gitlab.firma.example:8080/team/tool.git", parse("http://gitlab.firma.example:8080/team/tool.git").toWebUrl(false));
    }

    @Test
    public void plainHttpIsMarkedUnencrypted() {
        final RemoteUrl url = parse("http://10.0.2.2:8080/owner/repo.git");

        assertFalse(url.scheme().isEncrypted());
        assertEquals("http://10.0.2.2:8080/owner/repo.git", url.toCanonicalUrl());
    }

    @Test
    public void rejectsUnsupportedOrIncompleteInput() {
        assertEquals(Optional.empty(), RemoteUrl.parse(null));
        assertEquals(Optional.empty(), RemoteUrl.parse("   "));
        assertEquals(Optional.empty(), RemoteUrl.parse("ftp://example.org/a/b.git"));
        assertEquals(Optional.empty(), RemoteUrl.parse("file:///sdcard/repo.git"));
        assertEquals(Optional.empty(), RemoteUrl.parse("https://github.com/only-one-segment"));
        assertEquals(Optional.empty(), RemoteUrl.parse("https://github.com/a/../b"));
        assertEquals(Optional.empty(), RemoteUrl.parse("not a url"));
        assertEquals(Optional.empty(), RemoteUrl.parse("https:///owner/repo"));
    }

    private static RemoteUrl parse(
            final String raw
    ) {
        return RemoteUrl.parse(raw).orElseThrow(() -> new AssertionError("nicht parsebar: " + raw));
    }
}
