package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.HostKeyRejectedException;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.HostKeyChallenge;

import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.List;

public final class GitErrorMapperTest {

    @Test
    public void rejectedCredentialsAreAnAuthProblem() {
        final GitFailureException failure = GitErrorMapper.map(
                new TransportException("https://github.com/max/alpha.git: not authorized"), false);

        assertEquals(GitFailureKind.AUTH, failure.kind());
    }

    @Test
    public void aConnectionThatBrokeOffMidTransferIsANetworkProblemEvenWithAMisleadingText() {
        final GitFailureException failure = GitErrorMapper.map(
                new TransportException("transfer failed", new java.io.EOFException("\n not found: size=0 content=...")), false);

        assertEquals(GitFailureKind.NETWORK, failure.kind());
    }

    @Test
    public void aRepoNameThatContainsAStatusCodeIsNoReasonToBlameTheCredentials() {
        final GitFailureException failure = GitErrorMapper.map(
                new TransportException("https://git.example.org/team/projekt-403.git: Connection reset",
                        new java.net.SocketException("Connection reset")), false);

        assertEquals(GitFailureKind.NETWORK, failure.kind());
    }

    @Test
    public void aRealForbiddenStatusIsStillAnAuthProblem() {
        final GitFailureException failure = GitErrorMapper.map(
                new TransportException("https://git.example.org/team/projekt.git: 403 Forbidden"), false);

        assertEquals(GitFailureKind.AUTH, failure.kind());
    }

    @Test
    public void aTruncatedLocalFileIsNotAConnectionProblem() {
        final GitFailureException failure = GitErrorMapper.map(
                new IOException("Pack file is cut off", new java.io.EOFException("Unexpected end of file")), false);

        assertEquals(GitFailureKind.UNKNOWN, failure.kind());
    }

    @Test
    public void aMissingRepoIsNotFound() throws Exception {
        final GitFailureException failure = GitErrorMapper.map(
                new NoRemoteRepositoryException(new URIish("https://github.com/max/weg.git"), "not found"), false);

        assertEquals(GitFailureKind.NOT_FOUND, failure.kind());
    }

    @Test
    public void anUnreachableHostIsANetworkProblem() {
        final GitFailureException failure = GitErrorMapper.map(
                new TransportException("fetch failed", new UnknownHostException("github.com")), false);

        assertEquals(GitFailureKind.NETWORK, failure.kind());
    }

    @Test
    public void checkoutConflictsKeepTheirPaths() {
        final GitFailureException failure = GitErrorMapper.map(
                new CheckoutConflictException(List.of("a.txt", "b/c.txt"),
                        new org.eclipse.jgit.errors.CheckoutConflictException(new String[] {"a.txt", "b/c.txt"})), false);

        assertEquals(GitFailureKind.DIRTY_TREE, failure.kind());
        assertEquals(List.of("a.txt", "b/c.txt"), failure.paths());
    }

    @Test
    public void anEmptyCommitIsNothingToCommit() {
        assertEquals(GitFailureKind.NOTHING_TO_COMMIT,
                GitErrorMapper.map(new EmptyCommitException("nichts"), false).kind());
    }

    @Test
    public void aMissingHeadMeansNoUpstream() {
        assertEquals(GitFailureKind.NO_UPSTREAM, GitErrorMapper.map(new NoHeadException("kein HEAD"), false).kind());
    }

    @Test
    public void cancellationIsRecognisedFromTheExceptionAndFromTheFlag() {
        assertEquals(GitFailureKind.CANCELLED, GitErrorMapper.map(new CanceledException("abgebrochen"), false).kind());
        assertEquals(GitFailureKind.CANCELLED, GitErrorMapper.map(new IOException("irgendwas"), true).kind());
    }

    @Test
    public void storageProblemsAreTold() {
        assertEquals(GitFailureKind.DISK_FULL,
                GitErrorMapper.map(new IOException("write failed: No space left on device"), false).kind());
        assertEquals(GitFailureKind.INVALID_PATH,
                GitErrorMapper.map(new IOException("/storage/emulated/0/x/a:b.txt: Operation not permitted"), false).kind());
    }

    @Test
    public void everythingElseIsUnknownButKeepsTheMessage() {
        final GitFailureException failure = GitErrorMapper.map(new IOException("seltsam"), false);

        assertEquals(GitFailureKind.UNKNOWN, failure.kind());
        assertEquals("seltsam", failure.getMessage());
    }

    @Test
    public void anAlreadyMappedFailurePassesThrough() {
        final GitFailureException original = new GitFailureException(GitFailureKind.REJECTED, "abgelehnt", null);

        assertSame(original, GitErrorMapper.map(original, false));
    }

    @Test
    public void anUnresolvableSshHostIsANetworkProblem() {
        final GitFailureException failure = GitErrorMapper.map(
                new TransportException("git@github.com:max/alpha.git: Failed (UnresolvedAddressException) to execute: null",
                        new UnresolvedAddressException()), false);

        assertEquals(GitFailureKind.NETWORK, failure.kind());
    }

    @Test
    public void anUnknownSshServerIsReportedWithItsHost() {
        final HostKeyChallenge challenge = new HostKeyChallenge("git.example.com", "ssh-ed25519", "AAAA", "SHA256:x", List.of(), false);

        final GitFailureException failure = GitErrorMapper.map(
                new TransportException("ssh://git.example.com/a/b.git: Verbindung fehlgeschlagen",
                        new HostKeyRejectedException(challenge)), false);

        assertEquals(GitFailureKind.HOST_KEY_UNKNOWN, failure.kind());
        assertEquals(List.of("git.example.com"), failure.paths());
    }

    @Test
    public void aChangedSshServerKeyIsReportedAsChanged() {
        final HostKeyChallenge challenge = new HostKeyChallenge("git.example.com", "ssh-ed25519", "AAAA", "SHA256:neu",
                List.of("SHA256:alt"), false);

        final GitFailureException failure = GitErrorMapper.map(new HostKeyRejectedException(challenge), false);

        assertEquals(GitFailureKind.HOST_KEY_CHANGED, failure.kind());
        assertEquals(List.of("git.example.com"), failure.paths());
    }

    @Test
    public void aRejectedSshKeyIsNotAnAccountProblem() {
        final GitFailureException failure = GitErrorMapper.map(
                new TransportException("git@github.com:max/alpha.git: Auth fail for methods 'publickey'"), false);

        assertEquals(GitFailureKind.SSH_REJECTED, failure.kind());
    }
}
