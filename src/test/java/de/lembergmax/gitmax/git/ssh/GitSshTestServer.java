package de.lembergmax.gitmax.git.ssh;

import de.lembergmax.gitmax.domain.model.SshKeyType;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.AbstractCommandSupport;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ein SSH-Server nur für Tests: nimmt {@code git-upload-pack} und {@code git-receive-pack} entgegen und reicht sie an das
 * lokale {@code git} weiter, mit öffentlichem Schlüssel als einziger Anmeldung. Die Repos liegen als
 * {@code <wurzel>/<pfad>.git}. Mit {@link #main} lässt er sich auch von Hand starten (Emulator-Läufe).
 */
public final class GitSshTestServer implements AutoCloseable {

    private static final Pattern GIT_COMMAND = Pattern.compile("^git[- ](upload-pack|receive-pack) '?(.+?)'?$");

    private final SshServer server;

    private GitSshTestServer(
            final SshServer server
    ) {
        this.server = server;
    }

    /**
     * Startet den Server auf {@code 127.0.0.1} (Port 0: ein freier).
     *
     * @param root        Ordner mit den Bare-Repos
     * @param authorized  entscheidet bei jeder Anmeldung, ob der vorgelegte öffentliche Schlüssel eintreten darf
     * @param hostKey     Schlüssel des Servers
     */
    public static GitSshTestServer start(
            final File root,
            final int port,
            final Predicate<PublicKey> authorized,
            final KeyPair hostKey
    ) throws IOException {
        Objects.requireNonNull(root, "root");
        final SshServer server = SshServer.setUpDefaultServer();
        server.setHost("0.0.0.0");
        server.setPort(port);
        server.setKeyPairProvider(KeyPairProvider.wrap(hostKey));
        server.setPublickeyAuthenticator((user, key, session) -> authorized.test(key));
        server.setShellFactory(null);
        server.setCommandFactory(new GitCommandFactory(root));
        server.start();
        return new GitSshTestServer(server);
    }

    public int port() {
        return server.getPort();
    }

    @Override
    public void close() throws IOException {
        server.stop(true);
    }

    /** {@code git-upload-pack}/{@code git-receive-pack} für Pfade unterhalb der Wurzel; alles andere wird abgelehnt. */
    private static final class GitCommandFactory implements CommandFactory {

        private final File root;

        private GitCommandFactory(
                final File root
        ) {
            this.root = root;
        }

        @Override
        public Command createCommand(
                final ChannelSession channel,
                final String command
        ) throws IOException {
            final Matcher matcher = GIT_COMMAND.matcher(command.trim());
            if (!matcher.matches() || matcher.group(2).contains("..")) {
                throw new IOException("Befehl nicht erlaubt: " + command);
            }
            String path = matcher.group(2);
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            return new GitProcessCommand(command, matcher.group(1), new File(root, path));
        }
    }

    /** Führt {@code git <dienst> <ordner>} aus und verbindet seine Ein- und Ausgabe mit dem SSH-Kanal. */
    private static final class GitProcessCommand extends AbstractCommandSupport {

        private final String service;
        private final File directory;

        private GitProcessCommand(
                final String command,
                final String service,
                final File directory
        ) {
            super(command, null);
            this.service = service;
            this.directory = directory;
        }

        @Override
        public void run() {
            int exit = 1;
            try {
                final Process process = new ProcessBuilder("git", service, directory.getAbsolutePath()).start();
                final Thread input = pump(getInputStream(), process.getOutputStream(), true);
                final Thread errors = pump(process.getErrorStream(), getErrorStream(), false);
                final Thread output = pump(process.getInputStream(), getOutputStream(), false);
                output.join();
                errors.join();
                exit = process.waitFor();
                input.interrupt();
            } catch (final IOException | InterruptedException failure) {
                exit = 1;
            } finally {
                onExit(exit);
            }
        }

        private static Thread pump(
                final InputStream from,
                final OutputStream to,
                final boolean closeTarget
        ) {
            final Thread thread = new Thread(() -> {
                final byte[] buffer = new byte[16 * 1024];
                try {
                    int read = from.read(buffer);
                    while (read >= 0) {
                        to.write(buffer, 0, read);
                        to.flush();
                        read = from.read(buffer);
                    }
                } catch (final IOException closed) {
                    // Die Gegenseite hat geschlossen: das Ende der Übertragung.
                } finally {
                    if (closeTarget) {
                        try {
                            to.close();
                        } catch (final IOException ignored) {
                            // schon zu
                        }
                    }
                }
            }, "git-ssh-pump");
            thread.setDaemon(true);
            thread.start();
            return thread;
        }
    }

    /* ------------------------------------------------------------------------------------------ */

    /**
     * Von Hand starten: {@code <wurzel> <port> <authorized_keys-datei> <host-schlüssel-datei>}. Fehlt die
     * Host-Schlüssel-Datei, wird ein Ed25519-Schlüssel erzeugt und dort abgelegt.
     */
    public static void main(
            final String[] args
    ) throws Exception {
        final File root = new File(args[0]);
        final int port = Integer.parseInt(args[1]);
        final File authorizedFile = new File(args[2]);
        final File hostKeyFile = new File(args[3]);
        final KeyPair hostKey;
        if (hostKeyFile.isFile()) {
            hostKey = SshKeyCodec.decodePrivate(new String(Files.readAllBytes(hostKeyFile.toPath()), StandardCharsets.UTF_8), null);
        } else {
            hostKey = SshKeyCodec.generate(SshKeyType.ED25519);
            Files.write(hostKeyFile.toPath(), SshKeyCodec.encodePrivate(hostKey).getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("Host-Schlüssel: " + SshKeyCodec.fingerprint(hostKey.getPublic()));
        final GitSshTestServer running = start(root, port, key -> isAuthorized(authorizedFile, key), hostKey);
        System.out.println("SSH-Server läuft auf Port " + running.port());
        Thread.currentThread().join();
    }

    /** Liest die Datei bei jeder Anmeldung; eine Zeile {@code *} lässt jeden Schlüssel herein (nur für Läufe von Hand). */
    private static boolean isAuthorized(
            final File file,
            final PublicKey presented
    ) {
        try {
            final List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            if (lines.stream().anyMatch(line -> line.strip().equals("*"))) {
                return true;
            }
            for (final AuthorizedKeyEntry entry : AuthorizedKeyEntry.readAuthorizedKeys(file.toPath())) {
                if (KeyUtils.compareKeys(entry.resolvePublicKey(null, PublicKeyEntryResolver.IGNORING), presented)) {
                    return true;
                }
            }
        } catch (final IOException | GeneralSecurityException unreadable) {
            return false;
        }
        return false;
    }
}
