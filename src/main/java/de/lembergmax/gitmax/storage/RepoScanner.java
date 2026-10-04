package de.lembergmax.gitmax.storage;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.LocalRepo;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Sucht Git-Repos unterhalb der Arbeitsordner. Breitensuche bis zur Tiefe {@value #MAX_DEPTH}; in einem
 * Repo wird nicht weitergesucht, versteckte Ordner und bekannte Großverzeichnisse werden übersprungen.
 */
public final class RepoScanner {

    /** Tiefe unterhalb des Arbeitsordners: 0 = der Ordner selbst, 3 = {@code ordner/a/b/repo}. */
    static final int MAX_DEPTH = 3;

    private static final Set<String> SKIPPED_NAMES = Set.of("node_modules", "build", "android");

    private RepoScanner() {
    }

    /**
     * @param roots     Arbeitsordner
     * @param cancelled wird zwischendurch gefragt; bei {@code true} endet die Suche mit dem bisherigen Ergebnis
     * @return gefundene Repos, nach Name sortiert (ohne Beachtung der Groß-/Kleinschreibung)
     */
    @NonNull
    public static List<LocalRepo> scan(
            @NonNull final List<File> roots,
            @NonNull final BooleanSupplier cancelled
    ) {
        Objects.requireNonNull(roots, "roots");
        Objects.requireNonNull(cancelled, "cancelled");
        final List<LocalRepo> found = new ArrayList<>();
        for (final File root : roots) {
            if (cancelled.getAsBoolean()) {
                break;
            }
            scanRoot(root, found, cancelled);
        }
        removeDuplicates(found);
        found.sort(Comparator.comparing((LocalRepo repo) -> repo.relativePath().toLowerCase(Locale.ROOT)));
        return found;
    }

    /** Liegen Arbeitsordner ineinander, kommt ein Repo mehrfach vor; es bleibt der erste Fund. */
    private static void removeDuplicates(
            final List<LocalRepo> repos
    ) {
        final Set<String> seen = new HashSet<>();
        repos.removeIf(repo -> !seen.add(repo.directory().getAbsolutePath()));
    }

    private static void scanRoot(
            final File root,
            final List<LocalRepo> found,
            final BooleanSupplier cancelled
    ) {
        if (!root.isDirectory()) {
            return;
        }
        final Deque<Node> queue = new ArrayDeque<>();
        queue.add(new Node(root, 0));
        while (!queue.isEmpty() && !cancelled.getAsBoolean()) {
            final Node node = queue.poll();
            if (isRepo(node.directory)) {
                found.add(new LocalRepo(node.directory, root));
                continue;
            }
            if (node.depth >= MAX_DEPTH) {
                continue;
            }
            final File[] children = node.directory.listFiles(File::isDirectory);
            if (children == null) {
                continue;
            }
            for (final File child : children) {
                if (!isSkipped(child)) {
                    queue.add(new Node(child, node.depth + 1));
                }
            }
        }
    }

    private static boolean isRepo(
            final File directory
    ) {
        // .git ist ein Ordner, bei Submodulen und Worktrees eine Datei.
        return new File(directory, ".git").exists();
    }

    /** Versteckte Ordner und Großverzeichnisse; ein Repo, das nur so heißt (etwa {@code android}), bleibt sichtbar. */
    private static boolean isSkipped(
            final File directory
    ) {
        final String name = directory.getName();
        if (name.startsWith(".")) {
            return true;
        }
        return SKIPPED_NAMES.contains(name.toLowerCase(Locale.ROOT)) && !isRepo(directory);
    }

    private record Node(
            File directory,
            int depth
    ) {
    }
}
