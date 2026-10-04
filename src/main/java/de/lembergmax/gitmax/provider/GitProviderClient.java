package de.lembergmax.gitmax.provider;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.NewRepo;
import de.lembergmax.gitmax.domain.model.RemoteRepo;

import java.util.List;

/**
 * Zugriff auf die REST-API eines Git-Anbieters. Alle Methoden blockieren und gehören auf einen
 * Hintergrund-Thread. Der Token wird nie gespeichert und nie in Meldungen ausgegeben.
 */
public interface GitProviderClient {

    /**
     * Prüft den Token und liest das Profil des Inhabers.
     *
     * @throws ProviderException bei abgelehntem Token, Netzwerkfehler oder unlesbarer Antwort
     */
    @NonNull
    ProviderProfile fetchProfile(
            @NonNull AccountEndpoint endpoint,
            @NonNull String token
    ) throws ProviderException;

    /**
     * Listet alle Repos, die das Konto sieht (eigene, Mitarbeit, Organisationen bzw. Gruppen).
     *
     * @throws ProviderException                      bei API- oder Netzwerkfehlern
     * @throws java.util.concurrent.CancellationException wenn {@link ListListener#isCancelled()} wahr wird
     */
    @NonNull
    List<RemoteRepo> listRepositories(
            @NonNull String accountId,
            @NonNull AccountEndpoint endpoint,
            @NonNull String token,
            @NonNull ListListener listener
    ) throws ProviderException;

    /**
     * Legt ein leeres Repo im persönlichen Bereich des Kontos an.
     *
     * @return das neue Repo mit seinen Klon-Adressen
     * @throws ProviderException bei {@code NAME_TAKEN} (gibt es schon), {@code INVALID} (Name abgelehnt), fehlendem
     *                           Recht ({@code FORBIDDEN}) oder Netzwerkfehlern
     */
    @NonNull
    RemoteRepo createRepository(
            @NonNull String accountId,
            @NonNull AccountEndpoint endpoint,
            @NonNull String token,
            @NonNull NewRepo request
    ) throws ProviderException;

    /** Rückmeldung während einer Auflistung über mehrere Seiten. */
    interface ListListener {

        /** Tut nichts und bricht nie ab. */
        ListListener NONE = new ListListener() {
            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public void onProgress(
                    final int loaded
            ) {
                // bewusst leer: Aufrufer ohne Fortschrittsanzeige
            }
        };

        /** {@code true}, wenn die Auflistung abgebrochen werden soll. */
        boolean isCancelled();

        /** Anzahl bisher geladener Repos, nach jeder Seite. */
        void onProgress(int loaded);
    }
}
