package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.HostKeyRejectedException;
import de.lembergmax.gitmax.domain.model.HostKeyChallenge;
import de.lembergmax.gitmax.domain.model.GitFailureKind;

import org.eclipse.jgit.api.errors.CannotDeleteCurrentBranchException;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.StashApplyFailureException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.TransportException;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.net.ssl.SSLException;

/**
 * Übersetzt JGit-Ausnahmen in {@link GitFailureException} mit einer Art, die die Oberfläche in einen
 * Handlungshinweis verwandelt. Technische Details bleiben in der Meldung und in der Ursache.
 */
public final class GitErrorMapper {

    private GitErrorMapper() {
    }

    /**
     * @param failure   was JGit geworfen hat
     * @param cancelled {@code true}, wenn der Nutzer abgebrochen hatte (jeder Fehler gilt dann als Abbruch)
     */
    @NonNull
    public static GitFailureException map(
            @NonNull final Throwable failure,
            final boolean cancelled
    ) {
        Objects.requireNonNull(failure, "failure");
        if (failure instanceof GitFailureException already) {
            return already;
        }
        if (cancelled || causedBy(failure, CanceledException.class) || causedBy(failure, InterruptedIOException.class)) {
            return new GitFailureException(GitFailureKind.CANCELLED, "Cancelled", failure);
        }
        final HostKeyRejectedException hostKey = causeOf(failure, HostKeyRejectedException.class);
        if (hostKey != null) {
            final HostKeyChallenge challenge = hostKey.challenge();
            return new GitFailureException(challenge.changed() ? GitFailureKind.HOST_KEY_CHANGED : GitFailureKind.HOST_KEY_UNKNOWN,
                    hostKey.getMessage(), List.of(challenge.host()), failure);
        }
        final String message = describe(failure);
        final String lower = message.toLowerCase(Locale.ROOT);

        if (failure instanceof CheckoutConflictException conflict) {
            return new GitFailureException(GitFailureKind.DIRTY_TREE, message, conflict.getConflictingPaths(), failure);
        }
        if (failure instanceof InvalidRefNameException) {
            return new GitFailureException(GitFailureKind.INVALID_NAME, message, failure);
        }
        if (failure instanceof RefAlreadyExistsException) {
            return new GitFailureException(GitFailureKind.ALREADY_EXISTS, message, failure);
        }
        if (failure instanceof RefNotFoundException) {
            return new GitFailureException(GitFailureKind.NOT_FOUND, message, failure);
        }
        if (failure instanceof NotMergedException) {
            return new GitFailureException(GitFailureKind.NOT_MERGED, message, failure);
        }
        if (failure instanceof CannotDeleteCurrentBranchException) {
            return new GitFailureException(GitFailureKind.CURRENT_BRANCH, message, failure);
        }
        if (failure instanceof StashApplyFailureException) {
            return new GitFailureException(GitFailureKind.CONFLICT, message, failure);
        }
        if (failure instanceof EmptyCommitException) {
            return new GitFailureException(GitFailureKind.NOTHING_TO_COMMIT, message, failure);
        }
        if (failure instanceof NoHeadException) {
            return new GitFailureException(GitFailureKind.NO_UPSTREAM, message, failure);
        }
        if (causedBy(failure, RepositoryNotFoundException.class)) {
            return new GitFailureException(GitFailureKind.NOT_A_REPO, message, failure);
        }
        if (isSshKeyRejected(failure, lower)) {
            return new GitFailureException(GitFailureKind.SSH_REJECTED, message, failure);
        }
        if (isAuth(failure, lower)) {
            return new GitFailureException(GitFailureKind.AUTH, message, failure);
        }
        if (causedBy(failure, NoRemoteRepositoryException.class) || lower.contains("not found")) {
            return new GitFailureException(GitFailureKind.NOT_FOUND, message, failure);
        }
        if (isNetwork(failure)) {
            return new GitFailureException(GitFailureKind.NETWORK, message, failure);
        }
        if (lower.contains("no space left") || lower.contains("enospc")) {
            return new GitFailureException(GitFailureKind.DISK_FULL, message, failure);
        }
        if (lower.contains("operation not permitted") || lower.contains("invalid argument")) {
            return new GitFailureException(GitFailureKind.INVALID_PATH, message, failure);
        }
        return new GitFailureException(GitFailureKind.UNKNOWN, message, failure);
    }

    /** Der SSH-Server hat keinen der angebotenen Schlüssel angenommen. */
    private static boolean isSshKeyRejected(
            final Throwable failure,
            final String lower
    ) {
        return causedBy(failure, TransportException.class)
                && lower.contains("publickey")
                && (lower.contains("auth fail") || lower.contains("authentication") || lower.contains("no more"));
    }

    private static boolean isAuth(
            final Throwable failure,
            final String lower
    ) {
        if (!causedBy(failure, TransportException.class) && !causedBy(failure, NotSupportedException.class)) {
            return lower.contains("not authorized") || lower.contains("authentication");
        }
        return lower.contains("not authorized")
                || lower.contains("authentication")
                || lower.contains("401")
                || lower.contains("403")
                || lower.contains("auth fail")
                || lower.contains("permission denied")
                || lower.contains("credentials");
    }

    private static boolean isNetwork(
            final Throwable failure
    ) {
        final List<Class<? extends Throwable>> networkTypes = List.of(
                UnknownHostException.class, ConnectException.class, NoRouteToHostException.class,
                SocketTimeoutException.class, SocketException.class, SSLException.class,
                UnresolvedAddressException.class);
        for (final Class<? extends Throwable> type : networkTypes) {
            if (causedBy(failure, type)) {
                return true;
            }
        }
        return false;
    }

    /** Die verständlichste Meldung aus der Ursachenkette: die der innersten Ausnahme mit Text. */
    private static String describe(
            final Throwable failure
    ) {
        String best = failure.getClass().getSimpleName();
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                best = current.getMessage();
                if (current instanceof TransportException) {
                    return best;
                }
            }
            current = current.getCause();
        }
        return best;
    }

    private static <T extends Throwable> T causeOf(
            final Throwable failure,
            final Class<T> type
    ) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean causedBy(
            final Throwable failure,
            final Class<? extends Throwable> type
    ) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
