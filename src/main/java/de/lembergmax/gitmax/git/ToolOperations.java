package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.RepoStats;
import de.lembergmax.gitmax.domain.model.SubmoduleInfo;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.SubmoduleStatusCommand;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.submodule.SubmoduleStatusType;
import org.eclipse.jgit.submodule.SubmoduleWalk;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Wartung eines Repos: Aufräumen, Git-Daten verdichten, Ausschlüsse, Submodule, Abruf-Einstellung. */
final class ToolOperations {

    private static final String EXCLUDE_PATH = "info/exclude";

    ToolOperations() {
    }

    /** Dateien und Ordner, die {@code clean} löschen würde: nur nicht verfolgte, nicht ignorierte. */
    @NonNull
    List<String> cleanPreview(
            @NonNull final File repoDirectory
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final Set<String> paths = git.clean().setCleanDirectories(true).setDryRun(true).call();
            return new ArrayList<>(new TreeSet<>(paths));
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void clean(
            @NonNull final File repoDirectory,
            @NonNull final Collection<String> paths
    ) throws GitFailureException {
        if (paths.isEmpty()) {
            return;
        }
        try (Git git = JgitEngine.open(repoDirectory)) {
            final Set<String> wanted = new HashSet<>();
            for (final String path : paths) {
                // Die Vorschau nennt Ordner mit Schrägstrich am Ende, CleanCommand erwartet sie ohne.
                wanted.add(path.endsWith("/") ? path.substring(0, path.length() - 1) : path);
            }
            git.clean().setCleanDirectories(true).setPaths(wanted).call();
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    @NonNull
    RepoStats stats(
            @NonNull final File repoDirectory
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final org.eclipse.jgit.internal.storage.file.GC.RepoStatistics statistics =
                    new GC((FileRepository) git.getRepository()).getStatistics();
            return new RepoStats(statistics.numberOfLooseObjects, statistics.numberOfPackFiles,
                    statistics.sizeOfLooseObjects, statistics.sizeOfPackedObjects);
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    /**
     * Verdichtet die Git-Daten: Referenzen und Objekte packen, Überflüssiges entfernen. Ruft bewusst nicht
     * {@code GC.gc()} auf, das auf Android an {@code ProcessHandle} scheitert.
     */
    void gc(
            @NonNull final File repoDirectory
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final GC collector = new GC((FileRepository) git.getRepository());
            git.packRefs().setAll(true).call();
            collector.repack();
            collector.prunePacked();
            collector.prune(Collections.emptySet());
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    @NonNull
    String readExclude(
            @NonNull final File repoDirectory
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final File file = excludeFile(git.getRepository());
            return file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "";
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void writeExclude(
            @NonNull final File repoDirectory,
            @NonNull final String text
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final File file = excludeFile(git.getRepository());
            final File parent = file.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Could not create folder: " + parent);
            }
            Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    @NonNull
    List<SubmoduleInfo> submodules(
            @NonNull final File repoDirectory
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final Repository repository = git.getRepository();
            final Map<String, SubmoduleStatus> statuses = new SubmoduleStatusCommand(repository).call();
            final List<SubmoduleInfo> modules = new ArrayList<>();
            try (SubmoduleWalk walk = SubmoduleWalk.forIndex(repository)) {
                while (walk.next()) {
                    final SubmoduleStatus status = statuses.get(walk.getPath());
                    final String url = walk.getModulesUrl() == null ? "" : walk.getModulesUrl();
                    final boolean initialized = status != null && status.getType() != SubmoduleStatusType.UNINITIALIZED
                            && status.getType() != SubmoduleStatusType.MISSING;
                    final boolean upToDate = status != null && status.getType() == SubmoduleStatusType.INITIALIZED;
                    modules.add(new SubmoduleInfo(walk.getPath(), url, initialized, upToDate));
                }
            }
            return modules;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    boolean pruneOnFetch(
            @NonNull final File repoDirectory
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            return git.getRepository().getConfig().getBoolean("fetch", null, "prune", true);
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void setPruneOnFetch(
            @NonNull final File repoDirectory,
            final boolean prune
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final StoredConfig config = git.getRepository().getConfig();
            config.setBoolean("fetch", null, "prune", prune);
            config.save();
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    private static File excludeFile(
            final Repository repository
    ) throws GitFailureException {
        if (repository.isBare()) {
            throw new GitFailureException(GitFailureKind.NOT_A_REPO, "A bare repo has no exclude file", null);
        }
        return new File(repository.getDirectory(), EXCLUDE_PATH);
    }
}
