package de.lembergmax.gitmax.ui.workspace;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.ui.common.Event;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Verwaltet die Arbeitsordner für den Einstellungs-Bildschirm.
 */
public final class WorkspaceViewModel extends AndroidViewModel {

    /**
     * Stand der Arbeitsordner.
     *
     * @param roots         alle Arbeitsordner
     * @param defaultTarget Standard-Zielordner, {@code null} wenn es keinen gibt
     */
    public record State(
            @NonNull List<File> roots,
            @Nullable File defaultTarget
    ) {
    }

    private final ServiceLocator services;
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(List.of(), null));
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();

    public WorkspaceViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
    }

    @NonNull
    public LiveData<State> state() {
        return state;
    }

    @NonNull
    public LiveData<Event<String>> messages() {
        return messages;
    }

    public void reload() {
        services.io().execute(this::publish);
    }

    public void add(
            @NonNull final File folder
    ) {
        services.io().execute(() -> {
            if (services.workspace().roots().contains(folder.getAbsoluteFile())) {
                messages.postValue(new Event<>(getApplication().getString(R.string.workspace_error_exists)));
            } else {
                services.workspace().addRoot(folder);
            }
            publish();
        });
    }

    /** Nimmt einen entfernten Arbeitsordner wieder auf, auf Wunsch auch als Standard-Zielordner. */
    public void restore(
            @NonNull final File folder,
            final boolean asDefault
    ) {
        services.io().execute(() -> {
            services.workspace().addRoot(folder);
            if (asDefault) {
                services.workspace().setDefaultTarget(folder);
            }
            publish();
        });
    }

    public void remove(
            @NonNull final File folder
    ) {
        services.io().execute(() -> {
            services.workspace().removeRoot(folder);
            publish();
        });
    }

    public void makeDefault(
            @NonNull final File folder
    ) {
        services.io().execute(() -> {
            services.workspace().setDefaultTarget(folder);
            publish();
        });
    }

    private void publish() {
        final Optional<File> target = services.workspace().defaultTarget();
        state.postValue(new State(services.workspace().roots(), target.orElse(null)));
    }
}
