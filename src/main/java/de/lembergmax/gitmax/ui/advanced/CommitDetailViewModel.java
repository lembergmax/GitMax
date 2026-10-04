package de.lembergmax.gitmax.ui.advanced;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.CommitDetail;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.ResetMode;
import de.lembergmax.gitmax.ops.OperationEntry;
import de.lembergmax.gitmax.ops.OperationTexts;
import de.lembergmax.gitmax.ui.common.Event;

import java.io.File;

/**
 * Ein Commit mit seinen Dateien und den Aktionen darauf: auschecken, Branch oder Tag hier anlegen,
 * übernehmen, rückgängig machen und den Branch hierher zurücksetzen.
 */
public final class CommitDetailViewModel extends AndroidViewModel {

    /**
     * Ergebnis einer Aktion.
     *
     * @param text     Meldung für den Nutzer
     * @param conflict die Aktion hat Konflikte hinterlassen, die zu lösen sind
     */
    public record Message(
            @NonNull String text,
            boolean conflict
    ) {
    }

    /** Fallback-Identität für Tags, wenn weder ein Repo-Wert noch ein Konto vorhanden ist. */
    private static final CommitIdentity FALLBACK = new CommitIdentity("GitMax", "gitmax@localhost.invalid");

    private final ServiceLocator services;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<CommitDetail> detail = new MutableLiveData<>();
    private final MutableLiveData<Event<Message>> messages = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);

    private File repo;
    private String commitId = "";

    public CommitDetailViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
    }

    /** Legt den Commit fest und lädt ihn; ein zweiter Aufruf ändert nichts. */
    public void init(
            @NonNull final String directory,
            @NonNull final String id
    ) {
        if (repo != null) {
            return;
        }
        repo = new File(directory);
        commitId = id;
        services.io().execute(() -> {
            CommitDetail loaded = null;
            String failure = null;
            try {
                loaded = services.advanced().commit(repo, id);
            } catch (final GitFailureException failed) {
                failure = describe(failed);
            }
            final CommitDetail result = loaded;
            final String problem = failure;
            main.post(() -> {
                detail.setValue(result);
                loading.setValue(false);
                if (problem != null) {
                    messages.setValue(new Event<>(new Message(problem, false)));
                }
            });
        });
    }

    @NonNull
    public LiveData<CommitDetail> detail() {
        return detail;
    }

    @NonNull
    public LiveData<Boolean> loading() {
        return loading;
    }

    @NonNull
    public LiveData<Event<Message>> messages() {
        return messages;
    }

    @NonNull
    public File repo() {
        return repo;
    }

    @NonNull
    public String commitId() {
        return commitId;
    }

    public void checkout(
            final int successMessage
    ) {
        run(successMessage, () -> services.advanced().checkoutCommit(repo, commitId));
    }

    public void createBranch(
            @NonNull final String name,
            final boolean checkout,
            final int successMessage
    ) {
        run(successMessage, () -> services.advanced().createBranch(repo, name, commitId, checkout));
    }

    public void createTag(
            @NonNull final String name,
            @NonNull final String message,
            final int successMessage
    ) {
        run(successMessage, () -> services.advanced().createTag(repo, name, commitId,
                message.isBlank() ? null : message.strip(), services.identities().resolve(repo).orElse(FALLBACK)));
    }

    /** Übernimmt diesen Commit auf den aktuellen Branch. */
    public void cherryPick(
            final int successMessage
    ) {
        run(successMessage, () -> services.advanced().cherryPick(repo, commitId));
    }

    /** Legt einen Commit an, der diesen aufhebt. */
    public void revert(
            final int successMessage
    ) {
        run(successMessage, () -> services.advanced().revert(repo, commitId));
    }

    /** Setzt den aktuellen Branch auf diesen Commit. */
    public void reset(
            @NonNull final ResetMode mode,
            final int successMessage
    ) {
        run(successMessage, () -> services.advanced().reset(repo, commitId, mode));
    }

    /** Eine Git-Aktion, die scheitern darf. */
    private interface Task {

        void run() throws GitFailureException;
    }

    private void run(
            final int successMessage,
            @NonNull final Task task
    ) {
        services.io().execute(() -> {
            Message result;
            try {
                task.run();
                result = new Message(getApplication().getString(successMessage), false);
            } catch (final GitFailureException failed) {
                result = new Message(describe(failed), failed.kind() == GitFailureKind.CONFLICT);
            }
            final Message message = result;
            main.post(() -> messages.setValue(new Event<>(message)));
        });
    }

    private String describe(
            final GitFailureException failed
    ) {
        return OperationTexts.failure(getApplication(),
                new OperationEntry.Failure(failed.kind(), failed.getMessage(), failed.paths()));
    }
}
