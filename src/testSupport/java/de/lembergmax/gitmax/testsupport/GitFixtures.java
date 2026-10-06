package de.lembergmax.gitmax.testsupport;

import androidx.annotation.NonNull;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Bereitet Remotes für Gerätetests vor. Liegt in der App (nur Debug und {@code minified}, nie im
 * Release), damit R8 die JGit-Aufrufe zusammen mit der Engine sieht und die Tests gegen die
 * geschrumpfte App dieselben Wege nehmen wie die Nutzer.
 */
public final class GitFixtures {

    private static final PersonIdent IDENT = new PersonIdent("Fixture", "fixture@example.invalid");

    private GitFixtures() {
    }

    /** Legt ein leeres Bare-Repo mit dem Branch {@code main} an. */
    @NonNull
    public static File createBareRemote(
            @NonNull final File directory
    ) throws Exception {
        Git.init().setBare(true).setInitialBranch("main").setDirectory(directory).call().close();
        return directory;
    }

    /**
     * Füllt das Remote mit einem typischen Projekt: Umlaut, Leerzeichen, ausführbare Datei und ein
     * Symlink. Die Arbeitskopie entsteht in {@code scratch} (App-Speicher, dort sind Symlinks erlaubt)
     * und wird danach gelöscht.
     */
    public static void seedTypicalProject(
            @NonNull final File remote,
            @NonNull final File scratch
    ) throws Exception {
        seed(remote, scratch, work -> {
            write(new File(work, "README.md"), "# Beispiel\n");
            write(new File(work, "dir/file.txt"), "inner\n");
            write(new File(work, "sp ace.txt"), "space\n");
            write(new File(work, "ümläut.txt"), "umlaut\n");
            final File script = new File(work, "run.sh");
            write(script, "#!/bin/sh\n");
            if (!script.setExecutable(true)) {
                throw new IOException("Ausführbares Bit nicht setzbar");
            }
            Files.createSymbolicLink(new File(work, "link").toPath(), new File("README.md").toPath());
        });
    }

    /** Füllt das Remote mit einer einzigen Datei; der Pfad darf auf dem Telefonspeicher verbotene Zeichen haben. */
    public static void seedWithFile(
            @NonNull final File remote,
            @NonNull final File scratch,
            @NonNull final String path,
            @NonNull final String content
    ) throws Exception {
        seed(remote, scratch, work -> write(new File(work, path), content));
    }

    /**
     * Füllt das Remote mit {@code smallCount} kleinen und {@code largeCount} großen Dateien aus Zufallsbytes
     * (nicht komprimierbar) in einem normalen Repo, das direkt als Quelle für einen Klon dient. Dateien über der Stromgrenze von JGit werden beim Auschecken als Datenstrom gelesen.
     */
    public static void seedWithBinaryFiles(
            @NonNull final File repo,
            final int smallCount,
            final int largeCount,
            final int largeBytes
    ) throws Exception {
        try (Git git = Git.init().setInitialBranch("main").setDirectory(repo).call()) {
            final java.util.Random random = new java.util.Random(7);
            for (int i = 0; i < smallCount + largeCount; i++) {
                final byte[] content = new byte[i < largeCount ? largeBytes : 2048];
                random.nextBytes(content);
                Files.write(new File(repo, "datei" + i + ".bin").toPath(), content);
            }
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Ausgangsstand").setAuthor(IDENT).setCommitter(IDENT).call();
            // Als Paket packen: so liefert der Server im Test die Objekte wie ein echter.
            git.gc().call();
        }
    }

    /** Commit-Kennung des Branches im Bare-Repo. */
    @NonNull
    public static String branchHead(
            @NonNull final File remote,
            @NonNull final String branch
    ) throws Exception {
        try (Git git = Git.open(remote)) {
            return git.getRepository().resolve("refs/heads/" + branch).getName();
        }
    }

    private interface Filler {

        void fill(
                File work
        ) throws Exception;
    }

    private static void seed(
            final File remote,
            final File scratch,
            final Filler filler
    ) throws Exception {
        try (Git git = Git.init().setInitialBranch("main").setDirectory(scratch).call()) {
            filler.fill(scratch);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Ausgangsstand").setAuthor(IDENT).setCommitter(IDENT).call();
            git.push().setRemote(remote.toURI().toString()).add("main").call();
        } finally {
            FileUtils.delete(scratch, FileUtils.RECURSIVE | FileUtils.SKIP_MISSING | FileUtils.IGNORE_ERRORS);
        }
    }

    private static void write(
            final File file,
            final String content
    ) throws IOException {
        final File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Ordner nicht anlegbar: " + parent);
        }
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
