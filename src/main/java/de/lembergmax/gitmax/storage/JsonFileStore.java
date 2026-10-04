package de.lembergmax.gitmax.storage;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Legt kleine Textdateien (JSON-Caches) in einem Verzeichnis ab. Geschrieben wird erst in eine
 * Temp-Datei und dann atomar umbenannt, damit ein Abbruch nie eine halbe Datei hinterlässt.
 */
public final class JsonFileStore {

    /** Erlaubte Dateinamen: keine Pfadtrenner, damit kein Name aus dem Verzeichnis ausbrechen kann. */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final File directory;

    public JsonFileStore(
            @NonNull final File directory
    ) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /** Inhalt der Datei, leer wenn sie fehlt oder nicht lesbar ist (ein Cache darf kaputt sein). */
    @NonNull
    public Optional<String> read(
            @NonNull final String name
    ) {
        final File file = resolve(name);
        if (!file.isFile()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (final IOException unreadable) {
            return Optional.empty();
        }
    }

    /** Schreibt die Datei atomar. */
    public void write(
            @NonNull final String name,
            @NonNull final String text
    ) throws IOException {
        Objects.requireNonNull(text, "text");
        final File target = resolve(name);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create directory: " + directory);
        }
        final File temp = new File(directory, name + ".tmp");
        Files.write(temp.toPath(), text.getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public void delete(
            @NonNull final String name
    ) {
        final File file = resolve(name);
        if (file.isFile() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private File resolve(
            final String name
    ) {
        Objects.requireNonNull(name, "name");
        if (!SAFE_NAME.matcher(name).matches() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Invalid file name: " + name);
        }
        return new File(directory, name);
    }
}
