package de.lembergmax.gitmax.ui.local;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.RepoNames;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.ops.Operations;
import de.lembergmax.gitmax.repository.RepoCreator;

import java.io.File;
import java.util.List;
import java.util.Optional;

/** Bereitet das Anlegen eines neuen Repos vor: Konto und Zielordner wählen, prüfen und den Vorgang einreihen. */
public final class NewRepoSheetViewModel extends AndroidViewModel {

    /** Warum ein Auftrag nicht gestartet wurde. */
    public enum Refusal {
        NO_ACCOUNT,
        NO_TARGET,
        FOLDER_EXISTS
    }

    /**
     * Ergebnis des Absendens.
     *
     * @param started     der Vorgang ist eingereiht
     * @param nameProblem Problem des Namens oder {@code null}
     * @param refusal     anderer Grund, warum nichts startete, oder {@code null}
     */
    public record Result(
            boolean started,
            @Nullable RepoNames.Problem nameProblem,
            @Nullable Refusal refusal
    ) {
    }

    /**
     * Was das Sheet zeigt.
     *
     * @param accounts alle verbundenen Konten
     * @param account  gewähltes Konto oder {@code null}
     * @param roots    alle Arbeitsordner
     * @param target   gewählter Arbeitsordner oder {@code null}
     */
    public record State(
            @NonNull List<Account> accounts,
            @Nullable Account account,
            @NonNull List<File> roots,
            @Nullable File target
    ) {
    }

    private final ServiceLocator services;
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private Account account;
    private File target;

    public NewRepoSheetViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
        final List<Account> accounts = services.accounts().all();
        account = accounts.isEmpty() ? null : accounts.get(0);
        target = services.workspace().defaultTarget().orElse(null);
        publish();
    }

    @NonNull
    public LiveData<State> state() {
        return state;
    }

    public void setAccount(
            @NonNull final Account chosen
    ) {
        account = chosen;
        publish();
    }

    public void setTarget(
            @NonNull final File chosen
    ) {
        target = chosen;
        publish();
    }

    /** Der Ordner, in dem das Repo mit diesem Namen entstünde. */
    @NonNull
    public Optional<File> folderFor(
            @NonNull final String name
    ) {
        return target == null ? Optional.empty() : Optional.of(new File(target, name.strip()));
    }

    /** Prüft die Eingaben und reiht das Anlegen ein. */
    @NonNull
    public Result submit(
            @NonNull final String name,
            @NonNull final String description,
            final boolean isPrivate,
            final boolean withReadme
    ) {
        final String clean = name.strip();
        final Optional<RepoNames.Problem> problem = RepoNames.check(clean);
        if (problem.isPresent()) {
            return new Result(false, problem.get(), null);
        }
        if (account == null) {
            return new Result(false, null, Refusal.NO_ACCOUNT);
        }
        if (target == null) {
            return new Result(false, null, Refusal.NO_TARGET);
        }
        final File folder = new File(target, clean);
        if (folder.exists()) {
            return new Result(false, null, Refusal.FOLDER_EXISTS);
        }
        final RepoCreator.Request request = new RepoCreator.Request(account.id(), clean, description, isPrivate, withReadme, folder);
        services.operations().enqueue(Operations.createRepo(clean, folder,
                (engine, progress) -> services.repoCreator().create(request, engine, progress)));
        return new Result(true, null, null);
    }

    private void publish() {
        state.setValue(new State(services.accounts().all(), account, services.workspace().roots(), target));
    }
}
