package de.lembergmax.gitmax.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Optional;

/** Prüft, welche geteilten Texte als Klon-Adresse durchgehen. */
public final class ShareTargetTest {

    private static String accepted(
            final String text
    ) {
        final Optional<RemoteUrl> url = ShareTarget.extract(text);
        assertTrue("Erwartet eine Adresse in: " + text, url.isPresent());
        return url.get().toHttpsUrl();
    }

    @Test
    public void aPlainGitAddressIsAccepted() {
        assertEquals("https://github.com/max/alpha.git", accepted("https://github.com/max/alpha.git"));
    }

    @Test
    public void aGitlabPageAddressKeepsOnlyTheProjectPath() {
        assertEquals("https://gitlab.com/gruppe/untergruppe/projekt.git",
                accepted("https://gitlab.com/gruppe/untergruppe/projekt/-/tree/main/src"));
        assertEquals("https://gitlab.com/gruppe/projekt.git", accepted("Schau mal: https://gitlab.com/gruppe/projekt/-/issues/4."));
    }

    @Test
    public void aGithubWebAddressIsAccepted() {
        assertEquals("https://github.com/max/alpha.git", accepted("https://github.com/max/alpha"));
    }

    @Test
    public void githubPagesInsideTheRepoPointToTheRepo() {
        assertEquals("https://github.com/max/alpha.git", accepted("https://github.com/max/alpha/tree/main/src"));
        assertEquals("https://github.com/max/alpha.git", accepted("https://github.com/max/alpha/issues/12?x=1#top"));
    }

    @Test
    public void gitlabAddressesKeepTheirGroups() {
        assertEquals("https://gitlab.com/gruppe/untergruppe/repo.git", accepted("https://gitlab.com/gruppe/untergruppe/repo.git"));
    }

    @Test
    public void textAroundTheAddressIsIgnored() {
        assertEquals("https://github.com/max/alpha.git", accepted("Schau mal: https://github.com/max/alpha, das ist gut."));
        assertEquals("https://github.com/max/alpha.git", accepted("(https://github.com/max/alpha)"));
    }

    @Test
    public void theFirstUsableAddressWins() {
        assertEquals("https://github.com/max/eins.git", accepted("https://github.com/max/eins und https://github.com/max/zwei"));
    }

    @Test
    public void anAddressWithoutARepoIsSkippedForALaterOne() {
        assertEquals("https://github.com/max/alpha.git", accepted("https://github.com/ https://github.com/max/alpha"));
    }

    @Test
    public void httpIsRefused() {
        assertTrue(ShareTarget.extract("http://github.com/max/alpha.git").isEmpty());
    }

    @Test
    public void sshAddressesAreRefused() {
        assertTrue(ShareTarget.extract("git@github.com:max/alpha.git").isEmpty());
        assertTrue(ShareTarget.extract("ssh://git@github.com/max/alpha.git").isEmpty());
    }

    @Test
    public void credentialsInTheAddressAreRefused() {
        assertTrue(ShareTarget.extract("https://max:geheim@github.com/max/alpha.git").isEmpty());
        assertTrue(ShareTarget.extract("https://github.com@evil.example/max/alpha.git").isEmpty());
    }

    @Test
    public void otherSchemesAndGarbageAreRefused() {
        assertTrue(ShareTarget.extract("file:///sdcard/alpha").isEmpty());
        assertTrue(ShareTarget.extract("javascript:alert(1)").isEmpty());
        assertTrue(ShareTarget.extract("kein Link hier").isEmpty());
        assertTrue(ShareTarget.extract("").isEmpty());
        assertTrue(ShareTarget.extract(null).isEmpty());
    }

    @Test
    public void veryLongTextIsRefused() {
        assertTrue(ShareTarget.extract("https://github.com/max/alpha " + "x".repeat(3000)).isEmpty());
    }

    @Test
    public void pathTricksAreRefused() {
        assertTrue(ShareTarget.extract("https://example.com/../etc").isEmpty());
        assertTrue(ShareTarget.extract("https://example.com//x").isEmpty());
    }
}
