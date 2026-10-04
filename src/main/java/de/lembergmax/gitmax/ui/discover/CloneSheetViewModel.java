package de.lembergmax.gitmax.ui.discover;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.ClonePlanner;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.CloneRequest;
import de.lembergmax.gitmax.domain.model.UpdateRequest;
import de.lembergmax.gitmax.ops.Operations;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bereitet einen Klon-Auftrag vor: wählt den Zielordner, legt je Repo den Ordnernamen fest und reiht
 * beim Bestätigen die Vorgänge ein.
 */
public final class CloneSheetViewModel extends AndroidViewModel {

    /**
     * Was das Sheet zeigt.
     *
     * @param target Zielordner oder {@code null}, wenn es keinen Arbeitsordner gibt
     * @param roots  alle Arbeitsordner zur Auswahl
     * @param plans  Ziel je Repo
     */
    public record State(
            @Nullable File target,
            @NonNull List<File> roots,
            @NonNull List<ClonePlanner.Plan> plans
    ) {

        /** Zahl der Repos, die neu geklont werden. */
        public int newCount() {
            return (int) plans.stream().filter(plan -> !plan.alreadyCloned()).count();
        }

        /** Zahl der Repos, die schon im Zielordner liegen. */
        public int existingCount() {
            return plans.size() - newCount();
        }
    }

    private final ServiceLocator services;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(null, List.of(), List.of()));
    private List<RemoteUrl> urls = List.of();
    private File target;
    private boolean initialized;

    public CloneSheetViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
    }

    @NonNull
    public LiveData<State> state() {
        return state;
    }

    /** Legt die Repos fest; ein zweiter Aufruf (Drehung des Bildschirms) ändert nichts. */
    public void init(
            @NonNull final List<String> rawUrls
    ) {
        if (initialized) {
            return;
        }
        initialized = true;
        final List<RemoteUrl> parsed = new ArrayList<>();
        for (final String raw : rawUrls) {
            RemoteUrl.parse(raw).ifPresent(parsed::add);
        }
        urls = parsed;
        target = services.workspace().defaultTarget().orElse(null);
        replan();
    }

    public void setTarget(
            @NonNull final File newTarget
    ) {
        target = newTarget;
        replan();
    }

    /**
     * Reiht die Vorgänge ein: Neues wird geklont, Vorhandenes auf Wunsch aktualisiert.
     *
     * @return Zahl der eingereihten Vorgänge
     */
    public int start(
            final boolean shallow,
            final boolean submodules,
            final boolean updateExisting,
            final boolean overSsh
    ) {
        final State current = state.getValue();
        if (current == null) {
            return 0;
        }
        int started = 0;
        for (final ClonePlanner.Plan plan : current.plans()) {
            if (plan.alreadyCloned()) {
                if (updateExisting) {
                    services.operations().enqueue(Operations.update(new UpdateRequest(plan.target(),
                            services.repoSettings().updateStrategy(plan.target()), UpdateRequest.ConflictPolicy.ABORT, false)));
                    started += 1;
                }
            } else {
                services.operations().enqueue(Operations.clone(
                        new CloneRequest(overSsh ? sshForm(plan.url()) : plan.url(), plan.target(), null, shallow, submodules)));
                started += 1;
            }
        }
        return started;
    }

    /** {@code true}, wenn mindestens ein SSH-Schlüssel eingerichtet ist. */
    public boolean hasSshKey() {
        return !services.sshKeys().isEmpty();
    }

    /** Dieselbe Adresse über SSH; ohne brauchbare SSH-Form bleibt die ursprüngliche. */
    private static RemoteUrl sshForm(
            @NonNull final RemoteUrl url
    ) {
        return RemoteUrl.parse(url.toSshUrl()).orElse(url);
    }

    private void replan() {
        final File chosen = target;
        final List<RemoteUrl> toPlan = urls;
        final List<File> roots = services.workspace().roots();
        if (chosen == null) {
            state.setValue(new State(null, roots, List.of()));
            return;
        }
        services.io().execute(() -> {
            final List<ClonePlanner.Plan> plans = ClonePlanner.plan(toPlan, chosen, services.localRepos()::inspectFolder);
            main.post(() -> state.setValue(new State(chosen, roots, plans)));
        });
    }

    /** Der gewählte Zielordner, falls schon bestimmt. */
    @NonNull
    public Optional<File> target() {
        return Optional.ofNullable(target);
    }
}
