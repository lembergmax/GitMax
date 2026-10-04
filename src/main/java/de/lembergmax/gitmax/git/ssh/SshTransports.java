package de.lembergmax.gitmax.git.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.git.GitTransports;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * SSH für JGit mit MINA SSHD: Die Schlüssel kommen aus dem {@link SshKeyStore} (aus dem Speicher, nie aus
 * Dateien), die Server-Schlüssel prüft die {@link AppServerKeyDatabase}. Weder {@code ~/.ssh} noch eine
 * {@code ssh_config} des Geräts spielen eine Rolle.
 */
public final class SshTransports implements GitTransports {

    private final File home;
    private final SshKeyStore keys;
    private final AppServerKeyDatabase serverKeys;
    private SshdSessionFactory factory;

    /**
     * @param home Ordner, der als Heimatverzeichnis für SSH dient; er wird bei Bedarf angelegt
     */
    public SshTransports(
            @NonNull final File home,
            @NonNull final SshKeyStore keys,
            @NonNull final KnownHostsStore hosts
    ) {
        this.home = Objects.requireNonNull(home, "home");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.serverKeys = new AppServerKeyDatabase(Objects.requireNonNull(hosts, "hosts"));
    }

    @Nullable
    @Override
    public TransportConfigCallback forUrl(
            @NonNull final RemoteUrl url
    ) throws GitFailureException {
        if (url.scheme() != RemoteUrl.Scheme.SSH) {
            return null;
        }
        if (keys.isEmpty()) {
            throw new GitFailureException(GitFailureKind.SSH_NO_KEY, "No SSH key is set up yet", null);
        }
        final SshdSessionFactory sessions = sessions();
        return transport -> {
            if (transport instanceof SshTransport ssh) {
                ssh.setSshSessionFactory(sessions);
            }
        };
    }

    private synchronized SshdSessionFactory sessions() {
        if (factory == null) {
            final File sshDirectory = new File(home, ".ssh");
            if (!sshDirectory.isDirectory() && !sshDirectory.mkdirs()) {
                throw new IllegalStateException("Could not create the SSH folder: " + sshDirectory);
            }
            factory = new SshdSessionFactoryBuilder()
                    .setHomeDirectory(home)
                    .setSshDirectory(sshDirectory)
                    .setPreferredAuthentications("publickey")
                    .setDefaultIdentities(dir -> List.of())
                    .setDefaultKnownHostsFiles(dir -> List.of())
                    .setDefaultKeysProvider(dir -> keys.keyPairs())
                    .setServerKeyDatabase((homeDirectory, ssh) -> serverKeys)
                    .build(null);
        }
        return factory;
    }
}
