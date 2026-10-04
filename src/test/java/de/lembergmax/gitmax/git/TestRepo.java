package de.lembergmax.gitmax.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Kleines Hilfsmittel für Tests: ein lokales Repo mit Schreiben, Lesen und Commits. */
final class TestRepo {

    static final PersonIdent ANNA = new PersonIdent("Anna", "anna@example.invalid");
    static final PersonIdent BERND = new PersonIdent("Bernd", "bernd@example.invalid");

    private final File directory;

    private TestRepo(
            final File directory
    ) {
        this.directory = directory;
    }

    /** Legt in {@code directory} ein Repo auf {@code main} an. */
    static TestRepo init(
            final File directory
    ) throws Exception {
        Git.init().setInitialBranch("main").setDirectory(directory).call().close();
        return new TestRepo(directory);
    }

    File directory() {
        return directory;
    }

    Git open() throws IOException {
        return Git.open(directory);
    }

    void commit(
            final String path,
            final String content,
            final String message,
            final PersonIdent author
    ) throws Exception {
        write(path, content);
        try (Git git = open()) {
            git.add().addFilepattern(path).call();
            git.commit().setMessage(message).setAuthor(author).setCommitter(author).call();
        }
    }

    void remove(
            final String path,
            final String message,
            final PersonIdent author
    ) throws Exception {
        try (Git git = open()) {
            git.rm().addFilepattern(path).call();
            git.commit().setMessage(message).setAuthor(author).setCommitter(author).call();
        }
    }

    void checkout(
            final String branch
    ) throws Exception {
        try (Git git = open()) {
            git.checkout().setName(branch).call();
        }
    }

    void createBranch(
            final String branch
    ) throws Exception {
        try (Git git = open()) {
            git.checkout().setCreateBranch(true).setName(branch).call();
        }
    }

    void write(
            final String path,
            final String content
    ) throws IOException {
        final File file = new File(directory, path);
        final File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Ordner nicht anlegbar: " + parent);
        }
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    String read(
            final String path
    ) throws IOException {
        return new String(Files.readAllBytes(new File(directory, path).toPath()), StandardCharsets.UTF_8);
    }

    boolean exists(
            final String path
    ) {
        return new File(directory, path).exists();
    }

    String head() throws Exception {
        try (Git git = open()) {
            return git.getRepository().resolve("HEAD").getName();
        }
    }
}
