package de.lembergmax.gitmax.ui.accounts;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.model.Account;

import java.util.List;

/**
 * Hält die Liste der verknüpften Konten für den Konten-Bildschirm.
 */
public final class AccountsViewModel extends AndroidViewModel {

    private final ServiceLocator services;
    private final MutableLiveData<List<Account>> accounts = new MutableLiveData<>(List.of());

    public AccountsViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
    }

    @NonNull
    public LiveData<List<Account>> accounts() {
        return accounts;
    }

    /** Liest die Konten neu, z. B. nach dem Zurückkehren von einem anderen Bildschirm. */
    public void reload() {
        services.io().execute(() -> accounts.postValue(services.accounts().all()));
    }
}
