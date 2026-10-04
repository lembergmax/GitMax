package de.lembergmax.gitmax.ui.accounts;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.lembergmax.gitmax.BuildConfig;
import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.provider.ProviderException;
import de.lembergmax.gitmax.storage.VaultException;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.ProviderErrorTexts;

import java.util.Optional;

/**
 * Prüft Eingaben und verknüpft das Konto im Hintergrund. Meldet Fortschritt und genau ein Ergebnis.
 */
public final class ConnectViewModel extends AndroidViewModel {

    /** Welches Eingabefeld die Meldung betrifft. */
    public enum Field {
        NONE,
        HOST,
        TOKEN
    }

    /**
     * Ergebnis eines Verbindungsversuchs.
     *
     * @param success {@code true}, wenn das Konto verknüpft wurde
     * @param message bei Erfolg der Login, sonst die Fehlermeldung
     * @param field   bei Fehlern das Feld, an dem die Meldung hängt
     */
    public record Outcome(
            boolean success,
            @NonNull String message,
            @NonNull Field field
    ) {
    }

    private final ServiceLocator services;
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Event<Outcome>> outcome = new MutableLiveData<>();

    public ConnectViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
    }

    @NonNull
    public LiveData<Boolean> busy() {
        return busy;
    }

    @NonNull
    public LiveData<Event<Outcome>> outcome() {
        return outcome;
    }

    /**
     * @param provider     gewählter Anbieter
     * @param customServer {@code true}, wenn {@code hostInput} einen eigenen Server meint
     * @param hostInput    Eingabe im Server-Feld
     * @param token        eingefügter Token
     */
    public void connect(
            @NonNull final ProviderType provider,
            final boolean customServer,
            @NonNull final String hostInput,
            @NonNull final String token
    ) {
        if (Boolean.TRUE.equals(busy.getValue())) {
            return;
        }
        final Optional<AccountEndpoint> endpoint = AccountEndpoint.fromHostInput(
                provider, customServer ? hostInput : null, BuildConfig.TEST_BUILD);
        if (endpoint.isEmpty() || (customServer && hostInput.isBlank())) {
            outcome.setValue(new Event<>(new Outcome(false, text(R.string.connect_error_host), Field.HOST)));
            return;
        }
        if (token.isBlank()) {
            outcome.setValue(new Event<>(new Outcome(false, text(R.string.connect_error_token_empty), Field.TOKEN)));
            return;
        }

        busy.setValue(true);
        services.io().execute(() -> {
            final Outcome result = run(endpoint.get(), token, customServer);
            busy.postValue(false);
            outcome.postValue(new Event<>(result));
        });
    }

    private Outcome run(
            final AccountEndpoint endpoint,
            final String token,
            final boolean customServer
    ) {
        try {
            final Account account = services.accounts().connect(endpoint, token);
            return new Outcome(true, account.login(), Field.NONE);
        } catch (final ProviderException failure) {
            return new Outcome(false, ProviderErrorTexts.describe(getApplication(), failure), fieldFor(failure, customServer));
        } catch (final VaultException failure) {
            return new Outcome(false, ProviderErrorTexts.describe(getApplication(), failure), Field.NONE);
        }
    }

    /** Abgelehnte Token hängen am Token-Feld; Verbindungsprobleme am Server-Feld, wenn es eines gibt. */
    private static Field fieldFor(
            final ProviderException failure,
            final boolean customServer
    ) {
        switch (failure.kind()) {
            case UNAUTHORIZED:
            case FORBIDDEN:
                return Field.TOKEN;
            case NETWORK:
            case NOT_FOUND:
            case MALFORMED:
                return customServer ? Field.HOST : Field.NONE;
            default:
                return Field.NONE;
        }
    }

    private String text(
            final int resource
    ) {
        return getApplication().getString(resource);
    }
}
