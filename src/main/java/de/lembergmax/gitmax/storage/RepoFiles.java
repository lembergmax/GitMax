package de.lembergmax.gitmax.storage;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.FolderName;

import org.eclipse.jgit.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Dateien eines Repos: auflisten, anlegen, umbenennen, löschen. Jeder Pfad wird relativ zum Repo
 * angegeben (mit {@code /}) und muss im Repo bleiben; das {@code .git}-Verzeichnis ist tabu.
 */
public final class RepoFiles {

    private static final String GIT_DIRECTORY = ".git";

    /** Warum eine Dateioperation nicht möglich war. */
    public enum Reason {
        /** Der Name enthält verbotene Zeichen oder ist leer. */
        INVALID_NAME,
        /** Unter diesem Namen gibt es schon etwas. */
        EXISTS,
        /** Die Datei oder der Ordner existiert nicht. */
        NOT_FOUND,
        /** Der Pfad liegt außerhalb des Repos oder in {@code .git}. */
        PROTECTED,
        /** Das Dateisystem hat die Operation abgelehnt. */
        IO
    }

    /** Eine Dateioperation ist gescheitert. */
    public static final class FilesException extends Exception {

        private final Reason reason;

        FilesException(
                @NonNull final Reason reason,
                @NonNull final String message,
                final Throwable cause
        ) {
            super(message, cause);
            this.reason = reason;
        }

        @NonNull
        public Reason reason() {
            return reason;
        }
    }

    /**
     * Ein Eintrag der Liste.
     *
     * @param name      Name ohne Pfad
     * @param path      Pfad relativ zum Repo mit {@code /}
     * @param directory {@code true} für Ordner
     * @param size      Größe in Byte, 0 bei Ordnern
     */
    public record Entry(
            @NonNull String name,
            @NonNull String path,
            boolean directory,
            long size
    ) {
    }

    private final File root;

    public RepoFiles(
            @NonNull final File root
    ) {
        this.root = Objects.requireNonNull(root, "root");
    }

    /** Einträge eines Ordners, Ordner zuerst, dann nach Name ohne Beachtung der Groß-/Kleinschreibung. */
    @NonNull
    public List<Entry> list(
            @NonNull final String relativeDirectory
    ) throws FilesException {
        final File directory = resolve(relativeDirectory);
        final File[] children = directory.listFiles();
        if (children == null) {
            throw new FilesException(Reason.NOT_FOUND, "Folder unreadable: " + relativeDirectory, null);
        }
        final List<Entry> entries = new ArrayList<>(children.length);
        for (final File child : children) {
            if (child.getName().equalsIgnoreCase(GIT_DIRECTORY)) {
                continue;
            }
            entries.add(new Entry(child.getName(), join(relativeDirectory, child.getName()),
                    child.isDirectory(), child.isDirectory() ? 0L : child.length()));
        }
        entries.sort(Comparator.comparing((Entry entry) -> !entry.directory())
                .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
        return entries;
    }

    /** Die echte Datei zu einem Repo-Pfad; scheitert für Pfade außerhalb des Repos und in {@code .git}. */
    @NonNull
    public File resolve(
            @NonNull final String relativePath
    ) throws FilesException {
        for (final String segment : relativePath.split("/")) {
            if (segment.equals("..") || segment.equalsIgnoreCase(GIT_DIRECTORY)) {
                throw new FilesException(Reason.PROTECTED, "Path not allowed: " + relativePath, null);
            }
        }
        final File target = relativePath.isEmpty() ? root : new File(root, relativePath);
        try {
            final String rootPath = root.getCanonicalPath();
            final String targetPath = target.getCanonicalPath();
            if (!targetPath.equals(rootPath) && !targetPath.startsWith(rootPath + File.separator)) {
                throw new FilesException(Reason.PROTECTED, "Path is outside the repo: " + relativePath, null);
            }
        } catch (final IOException unresolvable) {
            throw new FilesException(Reason.IO, "Path cannot be resolved: " + relativePath, unresolvable);
        }
        return target;
    }

    /** Legt eine leere Datei an. */
    @NonNull
    public String createFile(
            @NonNull final String relativeDirectory,
            @NonNull final String name
    ) throws FilesException {
        final File file = newChild(relativeDirectory, name);
        try {
            if (!file.createNewFile()) {
                throw new FilesException(Reason.EXISTS, "Already exists: " + name, null);
            }
        } catch (final IOException failed) {
            throw new FilesException(Reason.IO, "Could not create file: " + name, failed);
        }
        return join(relativeDirectory, name.strip());
    }

    /** Legt einen Ordner an. */
    @NonNull
    public String createFolder(
            @NonNull final String relativeDirectory,
            @NonNull final String name
    ) throws FilesException {
        final File folder = newChild(relativeDirectory, name);
        if (folder.exists()) {
            throw new FilesException(Reason.EXISTS, "Already exists: " + name, null);
        }
        if (!folder.mkdir()) {
            throw new FilesException(Reason.IO, "Could not create folder: " + name, null);
        }
        return join(relativeDirectory, name.strip());
    }

    /**
     * Benennt um, im selben Ordner.
     *
     * @return der neue Pfad
     */
    @NonNull
    public String rename(
            @NonNull final String relativePath,
            @NonNull final String newName
    ) throws FilesException {
        final File source = resolve(relativePath);
        if (relativePath.isEmpty() || !source.exists()) {
            throw new FilesException(relativePath.isEmpty() ? Reason.PROTECTED : Reason.NOT_FOUND,
                    "Cannot be renamed: " + relativePath, null);
        }
        final String parent = parentOf(relativePath);
        final File target = newChild(parent, newName);
        try {
            Files.move(source.toPath(), target.toPath());
        } catch (final IOException failed) {
            throw new FilesException(Reason.IO, "Rename failed: " + relativePath, failed);
        }
        return join(parent, newName.strip());
    }

    /** Löscht eine Datei oder einen Ordner samt Inhalt. Das Repo selbst bleibt unangetastet. */
    public void delete(
            @NonNull final String relativePath
    ) throws FilesException {
        if (relativePath.isEmpty()) {
            throw new FilesException(Reason.PROTECTED, "The repo itself is not deleted here", null);
        }
        final File target = resolve(relativePath);
        if (!target.exists()) {
            throw new FilesException(Reason.NOT_FOUND, "Not found: " + relativePath, null);
        }
        try {
            FileUtils.delete(target, FileUtils.RECURSIVE);
        } catch (final IOException failed) {
            throw new FilesException(Reason.IO, "Delete failed: " + relativePath, failed);
        }
    }

    private File newChild(
            final String relativeDirectory,
            final String name
    ) throws FilesException {
        if (!FolderName.isValid(name) || name.strip().equalsIgnoreCase(GIT_DIRECTORY)) {
            throw new FilesException(Reason.INVALID_NAME, "Invalid name: " + name, null);
        }
        final File directory = resolve(relativeDirectory);
        final File child = new File(directory, name.strip());
        if (child.exists()) {
            throw new FilesException(Reason.EXISTS, "Already exists: " + name, null);
        }
        return child;
    }

    private static String join(
            final String directory,
            final String name
    ) {
        return directory.isEmpty() ? name : directory + "/" + name;
    }

    private static String parentOf(
            final String path
    ) {
        final int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }
}
