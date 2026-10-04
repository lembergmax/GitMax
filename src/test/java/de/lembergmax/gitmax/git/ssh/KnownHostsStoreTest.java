package de.lembergmax.gitmax.git.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.HostKeyChallenge;
import de.lembergmax.gitmax.domain.model.KnownHost;
import de.lembergmax.gitmax.git.ssh.KnownHostsStore.Verdict;
import de.lembergmax.gitmax.storage.MemoryKeyValueStore;

import org.junit.Test;

import java.util.List;

/** Prüft Vertrauen beim ersten Kontakt, geänderte Schlüssel und die veröffentlichten Werte der Anbieter. */
public final class KnownHostsStoreTest {

    private static final String GITHUB_ED25519 = "SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU";

    private final MemoryKeyValueStore backing = new MemoryKeyValueStore();
    private final KnownHostsStore hosts = new KnownHostsStore(backing, () -> 42L);

    @Test
    public void anUnknownServerIsUnknownAndLeavesAChallenge() {
        final Verdict verdict = hosts.evaluate("git.example.com", "ssh-ed25519", "AAAAkey1", "SHA256:eins");

        assertEquals(Verdict.UNKNOWN, verdict);
        final HostKeyChallenge challenge = hosts.pending("git.example.com").orElseThrow();
        assertEquals("SHA256:eins", challenge.fingerprint());
        assertFalse(challenge.changed());
        assertFalse(challenge.contradictsPublished());
        assertTrue("Vor dem Vertrauen ist nichts gemerkt", hosts.all().isEmpty());
    }

    @Test
    public void afterTrustingTheSameKeyIsKnownAndTheChallengeIsGone() {
        hosts.evaluate("git.example.com", "ssh-ed25519", "AAAAkey1", "SHA256:eins");
        hosts.trust(hosts.pending("git.example.com").orElseThrow());

        assertEquals(Verdict.KNOWN, hosts.evaluate("git.example.com", "ssh-ed25519", "AAAAkey1", "SHA256:eins"));
        assertTrue(hosts.pending("git.example.com").isEmpty());
        assertEquals(List.of(new KnownHost("git.example.com", "ssh-ed25519", "AAAAkey1", "SHA256:eins", false, 42L)), hosts.all());
    }

    @Test
    public void aDifferentKeyOfAKnownTypeIsAChange() {
        hosts.evaluate("git.example.com", "ssh-ed25519", "AAAAkey1", "SHA256:eins");
        hosts.trust(hosts.pending("git.example.com").orElseThrow());

        final Verdict verdict = hosts.evaluate("git.example.com", "ssh-ed25519", "AAAAkey2", "SHA256:zwei");

        assertEquals(Verdict.CHANGED, verdict);
        final HostKeyChallenge challenge = hosts.pending("git.example.com").orElseThrow();
        assertTrue(challenge.changed());
        assertEquals(List.of("SHA256:eins"), challenge.knownFingerprints());
        assertEquals("Der alte Schlüssel gilt weiter", Verdict.KNOWN,
                hosts.evaluate("git.example.com", "ssh-ed25519", "AAAAkey1", "SHA256:eins"));
    }

    @Test
    public void trustingAChangeReplacesTheOldKeyOfThatType() {
        hosts.evaluate("git.example.com", "ssh-ed25519", "AAAAkey1", "SHA256:eins");
        hosts.trust(hosts.pending("git.example.com").orElseThrow());
        hosts.evaluate("git.example.com", "ssh-ed25519", "AAAAkey2", "SHA256:zwei");

        hosts.trust(hosts.pending("git.example.com").orElseThrow());

        assertEquals(1, hosts.all().size());
        assertEquals("SHA256:zwei", hosts.all().get(0).fingerprint());
    }

    @Test
    public void anotherKeyTypeForAKnownServerIsNewNotChanged() {
        hosts.evaluate("git.example.com", "ssh-ed25519", "AAAAkey1", "SHA256:eins");
        hosts.trust(hosts.pending("git.example.com").orElseThrow());

        assertEquals(Verdict.UNKNOWN, hosts.evaluate("git.example.com", "ssh-rsa", "AAAArsa", "SHA256:rsa"));
    }

    @Test
    public void aPublishedFingerprintIsTrustedWithoutAsking() {
        final Verdict verdict = hosts.evaluate("github.com", "ssh-ed25519", "AAAAgithub", GITHUB_ED25519);

        assertEquals(Verdict.TRUSTED_BY_PUBLICATION, verdict);
        assertTrue(hosts.all().get(0).verified());
        assertEquals(Verdict.KNOWN, hosts.evaluate("github.com", "ssh-ed25519", "AAAAgithub", GITHUB_ED25519));
        assertTrue(hosts.pending("github.com").isEmpty());
    }

    @Test
    public void anotherKeyForAPublishedHostIsFlaggedAsContradiction() {
        final Verdict verdict = hosts.evaluate("github.com", "ssh-ed25519", "AAAAfake", "SHA256:gefaelscht");

        assertEquals(Verdict.UNKNOWN, verdict);
        assertTrue(hosts.pending("github.com").orElseThrow().contradictsPublished());
    }

    @Test
    public void aPublishedKeyReplacesAnOlderKnownKeyOfThatType() {
        hosts.evaluate("github.com", "ssh-ed25519", "AAAAold", "SHA256:alt");
        hosts.trust(hosts.pending("github.com").orElseThrow());

        final Verdict verdict = hosts.evaluate("github.com", "ssh-ed25519", "AAAAnew", GITHUB_ED25519);

        assertEquals(Verdict.TRUSTED_BY_PUBLICATION, verdict);
        assertEquals(1, hosts.all().size());
        assertEquals(GITHUB_ED25519, hosts.all().get(0).fingerprint());
    }

    @Test
    public void thePublishedValuesDoNotApplyToAnotherPort() {
        final Verdict verdict = hosts.evaluate("[github.com]:2222", "ssh-ed25519", "AAAAgithub", GITHUB_ED25519);

        assertEquals(Verdict.UNKNOWN, verdict);
        assertFalse(hosts.pending("[github.com]:2222").orElseThrow().contradictsPublished());
    }

    @Test
    public void removingForgetsTheKey() {
        hosts.evaluate("git.example.com", "ssh-ed25519", "AAAAkey1", "SHA256:eins");
        hosts.trust(hosts.pending("git.example.com").orElseThrow());

        hosts.remove("git.example.com", "SHA256:eins");

        assertTrue(hosts.all().isEmpty());
        assertEquals(Verdict.UNKNOWN, hosts.evaluate("git.example.com", "ssh-ed25519", "AAAAkey1", "SHA256:eins"));
    }

    @Test
    public void removingAHostForgetsAllItsKeys() {
        hosts.evaluate("git.example.com", "ssh-ed25519", "AAAAkey1", "SHA256:eins");
        hosts.trust(hosts.pending("git.example.com").orElseThrow());
        hosts.evaluate("git.example.com", "ssh-rsa", "AAAArsa", "SHA256:rsa");
        hosts.trust(hosts.pending("git.example.com").orElseThrow());

        hosts.removeHost("git.example.com");

        assertTrue(hosts.forHost("git.example.com").isEmpty());
    }

    @Test
    public void trustSurvivesANewInstanceOnTheSameStorage() {
        hosts.evaluate("git.example.com", "ssh-ed25519", "AAAAkey1", "SHA256:eins");
        hosts.trust(hosts.pending("git.example.com").orElseThrow());
        hosts.evaluate("other.example.com", "ssh-ed25519", "AAAAkey9", "SHA256:neun");

        final KnownHostsStore reopened = new KnownHostsStore(backing, () -> 43L);

        assertEquals(Verdict.KNOWN, reopened.evaluate("git.example.com", "ssh-ed25519", "AAAAkey1", "SHA256:eins"));
        assertEquals("SHA256:neun", reopened.pending("other.example.com").orElseThrow().fingerprint());
    }

    @Test
    public void damagedStorageMeansEveryServerIsUnknownAgain() {
        backing.put("ssh.hosts", "{kaputt");
        backing.put("ssh.pending", "[[");

        assertTrue(hosts.all().isEmpty());
        assertTrue(hosts.pending("git.example.com").isEmpty());
        assertEquals(Verdict.UNKNOWN, hosts.evaluate("git.example.com", "ssh-ed25519", "AAAAkey1", "SHA256:eins"));
    }
}
