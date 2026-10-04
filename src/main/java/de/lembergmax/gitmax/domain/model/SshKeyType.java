package de.lembergmax.gitmax.domain.model;

/** Art eines SSH-Schlüssels, den GitMax erzeugt. */
public enum SshKeyType {

    /** Ed25519: kurz, schnell, von GitHub und GitLab unterstützt (Standard). */
    ED25519,
    /** RSA mit 4096 Bit: für Server, die Ed25519 nicht kennen. */
    RSA,
    /** ECDSA: GitMax erzeugt ihn nicht, kann ihn aber importieren. */
    ECDSA
}
