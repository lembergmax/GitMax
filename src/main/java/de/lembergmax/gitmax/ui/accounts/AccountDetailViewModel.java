package de.lembergmax.gitmax.ui.accounts;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.provider.ProviderException;
import de.lembergmax.gitmax.repository.AccountMismatchException;
import de.lembergmax.gitmax.storage.VaultException;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.ProviderErrorTexts;

import java.util.Optional;

/**
 * Lädt ein Konto und führt seine Änderungen aus: Identität speichern, Token erneuern, Konto entfernen.
 */
public final class AccountDetailViewModel extends AndroidViewModel {

    /** Art einer einmaligen Rückmeldung an die Oberfläche. */
    public enum Kind {
        IDENTITY_SAVED,
        NAME_INVALID,
        EMAIL_INVALID,
        RENEWED,
        RENEW_FAILED,
        REMOVED
    }

    /**
     * Einmalige Rückmeldung.
     *
     * @param kind Art
     * @param text anzuzeigender Text, leer wenn die Oberfläche selbst formuliert
     */
    public record Result(
            @NonNull Kind kind,
            @NonNull String text
    ) {
    }

    private final ServiceLocator services;
    private final MutableLiveData<Account> account = new MutableLiveData<>();
    private final MutableLiveData<Boolean> missing = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Event<Result>> results = new MutableLiveData<>();
    private String accountId;

    public AccountDetailViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
    }

    @NonNull
    public LiveData<Account> account() {
        return account;
    }

    /** {@code true}, wenn das Konto beim Laden nicht mehr existierte. */
    @NonNull
    public LiveData<Boolean> missing() {
        return missing;
    }

    @NonNull
    public LiveData<Boolean> busy() {
        return busy;
    }

    @NonNull
    public LiveData<Event<Result>> results() {
        return results;
    }

    public void load(
            @NonNull final String id
    ) {
        this.accountId = id;
        services.io().execute(() -> {
            final Optional<Account> found = services.accounts().find(id);
            if (found.isPresent()) {
                account.postValue(found.get());
            } else {
                missing.postValue(true);
            }
        });
    }

    public void saveIdentity(
            @NonNull final String name,
            @NonNull final String email
    ) {
        if (name.isBlank()) {
            results.setValue(new Event<>(new Result(Kind.NAME_INVALID, "")));
            return;
        }
        if (!CommitIdentity.isPlausibleEmail(email.trim())) {
            results.setValue(new Event<>(new Result(Kind.EMAIL_INVALID, "")));
            return;
        }
        final CommitIdentity identity = new CommitIdentity(name, email);
        services.io().execute(() -> {
            services.accounts().updateIdentity(accountId, identity);
            services.accounts().find(accountId).ifPresent(account::postValue);
            results.postValue(new Event<>(new Result(Kind.IDENTITY_SAVED, "")));
        });
    }

    public void renewToken(
            @NonNull final String token
    ) {
        if (Boolean.TRUE.equals(busy.getValue())) {
            return;
        }
        busy.setValue(true);
        services.io().execute(() -> {
            final Result result = runRenew(token);
            busy.postValue(false);
            results.postValue(new Event<>(result));
        });
    }

    public void remove() {
        services.io().execute(() -> {
            services.accounts().remove(accountId);
            services.remoteRepos().forget(accountId);
            results.postValue(new Event<>(new Result(Kind.REMOVED, "")));
        });
    }

    private Result runRenew(
            @NonNull final String token
    ) {
        try {
            account.postValue(services.accounts().replaceToken(accountId, token));
            return new Result(Kind.RENEWED, "");
        } catch (final ProviderException failure) {
            return new Result(Kind.RENEW_FAILED, ProviderErrorTexts.describe(getApplication(), failure));
        } catch (final VaultException failure) {
            return new Result(Kind.RENEW_FAILED, ProviderErrorTexts.describe(getApplication(), failure));
        } catch (final AccountMismatchException mismatch) {
            return new Result(Kind.RENEW_FAILED, getApplication().getString(R.string.account_detail_mismatch, mismatch.foundLogin()));
        } catch (final IllegalArgumentException blank) {
            return new Result(Kind.RENEW_FAILED, getApplication().getString(R.string.connect_error_token_empty));
        }
    }
}
