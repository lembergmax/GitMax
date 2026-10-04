package de.lembergmax.gitmax.domain;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Legt fest, in welchen Ordner jedes Repo eines Klon-Auftrags kommt. Alle Repos landen flach im
 * selben Zielordner; bei Namensgleichheit gilt die Regel aus {@link RepoNaming}. Auch zwei Repos
 * desselben Auftrags nehmen sich nie den Ordner weg.
 */
public final class ClonePlanner {

    /** Prüft, was unter einem Ordnernamen im Zielordner liegt. */
    public interface FolderInspector {

        @NonNull
        RepoNaming.FolderState inspect(
                @NonNull File folder,
                @NonNull RemoteUrl url
        );
    }

    /**
     * Ziel eines Repos.
     *
     * @param url           Quelle
     * @param target        Zielordner des Klons
     * @param alreadyCloned {@code true}, wenn dort schon dasselbe Repo liegt (dann wird nicht geklont)
     */
    public record Plan(
            @NonNull RemoteUrl url,
            @NonNull File target,
            boolean alreadyCloned
    ) {

        public Plan {
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(target, "target");
        }

        /** Name des Zielordners. */
        @NonNull
        public String folderName() {
            return target.getName();
        }
    }

    private ClonePlanner() {
    }

    /**
     * @param urls      die zu klonenden Repos; Doppelte (gleiches Repo) zählen einmal
     * @param root      Zielordner, in dem die Repos nebeneinander liegen
     * @param inspector prüft Ordner im Zielordner
     * @return je Repo ein Ziel, in der Reihenfolge der Eingabe
     */
    @NonNull
    public static List<Plan> plan(
            @NonNull final List<RemoteUrl> urls,
            @NonNull final File root,
            @NonNull final FolderInspector inspector
    ) {
        Objects.requireNonNull(urls, "urls");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(inspector, "inspector");

        final List<RemoteUrl> distinct = new ArrayList<>();
        for (final RemoteUrl url : urls) {
            if (distinct.stream().noneMatch(url::sameRepoAs)) {
                distinct.add(url);
            }
        }
        // Der Telefonspeicher unterscheidet Groß- und Kleinschreibung nicht, die Namen hier auch nicht.
        final Set<String> reserved = new HashSet<>();
        final List<Plan> plans = new ArrayList<>(distinct.size());
        for (final RemoteUrl url : distinct) {
            final RepoNaming.Result result = RepoNaming.resolve(url, name ->
                    reserved.contains(name.toLowerCase(Locale.ROOT))
                            ? RepoNaming.FolderState.OTHER
                            : inspector.inspect(new File(root, name), url));
            reserved.add(result.folderName().toLowerCase(Locale.ROOT));
            plans.add(new Plan(url, new File(root, result.folderName()), result.alreadyCloned()));
        }
        return plans;
    }
}
