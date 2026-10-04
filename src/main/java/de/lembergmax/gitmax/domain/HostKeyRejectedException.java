package de.lembergmax.gitmax.domain;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.HostKeyChallenge;

import java.util.Objects;

/**
 * Der SSH-Server hat einen Schlüssel vorgelegt, dem GitMax nicht vertraut. Die Verbindung kommt nicht zustande;
 * die Herausforderung liegt im {@code KnownHostsStore}, damit die Oberfläche sie zeigen kann.
 */
public final class HostKeyRejectedException extends RuntimeException {

    private final transient HostKeyChallenge challenge;

    public HostKeyRejectedException(
            @NonNull final HostKeyChallenge challenge
    ) {
        super((challenge.changed() ? "The server's key has changed: " : "Unknown server: ")
                + challenge.host() + " (" + challenge.fingerprint() + ")");
        this.challenge = Objects.requireNonNull(challenge, "challenge");
    }

    @NonNull
    public HostKeyChallenge challenge() {
        return challenge;
    }
}
