package de.lembergmax.gitmax.ui.discover;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.TransportPolicy;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.LocalRepo;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.domain.model.RemoteRepo;
import de.lembergmax.gitmax.provider.GitProviderClient;
import de.lembergmax.gitmax.provider.ProviderException;
import de.lembergmax.gitmax.storage.RemoteRepoJsonCodec;
import de.lembergmax.gitmax.storage.StoragePermission;
import de.lembergmax.gitmax.storage.VaultException;
import de.lembergmax.gitmax.ui.common.ProviderErrorTexts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hält die Repo-Listen aller Konten für den Entdecken-Bildschirm: zeigt sofort den zwischengespeicherten
 * Stand, lädt dann frisch nach, filtert, sortiert, sucht und merkt sich die Auswahl. Der Zustand wird
 * nur auf dem Hauptthread verändert; Hintergrundarbeit meldet ihr Ergebnis dorthin zurück.
 */
public final class DiscoverViewModel extends AndroidViewModel {

    /** Eingrenzung der Liste. */
    public enum Filter {
        ALL,
        NOT_CLONED,
        PRIVATE,
        PUBLIC,
        FORKS,
        ARCHIVED
    }

    /** Reihenfolge der Liste. */
    public enum Sort {
        RECENT,
        NAME
    }

    /**
     * Konto-Filter in der Chip-Zeile.
     *
     * @param id    Kennung des Kontos
     * @param label Beschriftung wie {@code GitHub · max}
     */
    public record AccountChip(
            @NonNull String id,
            @NonNull String label
    ) {
    }

    /**
     * Eine Zeile der Liste.
     *
     * @param repo     das Repo
     * @param provider Anbieter des Kontos, über das es sichtbar ist
     * @param cloned   {@code true}, wenn es schon in einem Arbeitsordner liegt
     * @param selected {@code true}, wenn es für das Klonen markiert ist
     */
    public record Row(
            @NonNull RemoteRepo repo,
            @NonNull ProviderType provider,
            boolean cloned,
            boolean selected
    ) {

        /** Schlüssel für die Auswahl: Kennung gilt nur innerhalb eines Kontos. */
        @NonNull
        public String key() {
            return keyOf(repo);
        }
    }

    /**
     * Alles, was der Bildschirm zeichnet.
     *
     * @param loading         eine Aktualisierung läuft
     * @param noAccounts      es ist kein Konto verbunden
     * @param accountChips    Konto-Filter; leer bei höchstens einem Konto
     * @param selectedAccount gewähltes Konto oder {@code null} für alle
     * @param filter          gewählter Filter
     * @param sort            gewählte Reihenfolge
     * @param query           Suchtext
     * @param rows            die sichtbaren Zeilen
     * @param selectedCount   Zahl der markierten Repos (auch ausgeblendete)
     * @param total           Zahl aller Repos vor Filter und Suche
     * @param error           Meldung, wenn das Laden zumindest teilweise fehlschlug
     * @param fetchedAt       Zeitpunkt des ältesten Standes in Millisekunden, 0 wenn unbekannt
     */
    public record UiState(
            boolean loading,
            boolean noAccounts,
            @NonNull List<AccountChip> accountChips,
            @Nullable String selectedAccount,
            @NonNull Filter filter,
            @NonNull Sort sort,
            @NonNull String query,
            @NonNull List<Row> rows,
            int selectedCount,
            int total,
            @Nullable String error,
            long fetchedAt
    ) {
    }

    private static final String LOG_TAG = "GitMaxDiscover";
    private static final char KEY_SEPARATOR = '|';

    private final ServiceLocator services;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private final AtomicInteger generation = new AtomicInteger();

    private List<Account> accounts = List.of();
    private final Map<String, RemoteRepoJsonCodec.Snapshot> snapshots = new HashMap<>();
    private final Set<String> clonedKeys = new HashSet<>();
    private final Set<String> selection = new LinkedHashSet<>();
    private String error;
    private boolean loading;
    private boolean started;
    private String selectedAccount;
    private Filter filter = Filter.ALL;
    private Sort sort = Sort.RECENT;
    private String query = "";

    public DiscoverViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
        publish();
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }

    /** Lädt beim ersten Aufruf; danach nur, wenn sich die Konten geändert haben. */
    public void start() {
        final List<Account> current = services.accounts().all();
        if (started && current.equals(accounts)) {
            reloadClonedKeys();
            return;
        }
        started = true;
        accounts = current;
        snapshots.keySet().retainAll(idsOf(current));
        if (selectedAccount != null && !idsOf(current).contains(selectedAccount)) {
            selectedAccount = null;
        }
        // Markierungen von Repos eines entfernten Kontos zählen sonst weiter mit, ohne dass man sie sehen oder lösen kann.
        selection.removeIf(key -> !idsOf(current).contains(key.substring(0, Math.max(0, key.indexOf(KEY_SEPARATOR)))));
        publish();
        refresh();
    }

    /** Liest die Listen frisch von den Anbietern; der zwischengespeicherte Stand bleibt bis dahin sichtbar. */
    public void refresh() {
        final int current = generation.incrementAndGet();
        if (accounts.isEmpty()) {
            loading = false;
            publish();
            return;
        }
        loading = true;
        error = null;
        publish();
        final List<Account> targets = new ArrayList<>(accounts);
        services.io().execute(() -> load(current, targets));
        reloadClonedKeys();
    }

    public void setQuery(
            @NonNull final String newQuery
    ) {
        query = newQuery.trim();
        publish();
    }

    public void setFilter(
            @NonNull final Filter newFilter
    ) {
        filter = newFilter;
        publish();
    }

    public void setSort(
            @NonNull final Sort newSort
    ) {
        sort = newSort;
        publish();
    }

    /** Ob die Adresse über ihren Übertragungsweg geklont werden darf: HTTP nur zu einem unverschlüsselt verknüpften Server. */
    public boolean allowsTransport(
            @NonNull final RemoteUrl url
    ) {
        return TransportPolicy.allows(url, services.accounts().all().stream().map(Account::endpoint).toList());
    }

    public void selectAccount(
            @Nullable final String accountId
    ) {
        selectedAccount = accountId;
        publish();
    }

    public void toggleSelection(
            @NonNull final String key
    ) {
        if (!selection.remove(key)) {
            selection.add(key);
        }
        publish();
    }

    public void clearSelection() {
        selection.clear();
        publish();
    }

    public void selectAllVisible() {
        final UiState current = state.getValue();
        if (current == null) {
            return;
        }
        for (final Row row : current.rows()) {
            selection.add(row.key());
        }
        publish();
    }

    /** Die HTTPS-Adressen der markierten Repos, in der Reihenfolge der Markierung. */
    @NonNull
    public List<String> selectedUrls() {
        final List<String> urls = new ArrayList<>();
        for (final String key : selection) {
            final RemoteRepo repo = find(key);
            if (repo != null) {
                urls.add(repo.httpsUrl());
            }
        }
        return urls;
    }

    @NonNull
    public static String keyOf(
            @NonNull final RemoteRepo repo
    ) {
        return repo.accountId() + KEY_SEPARATOR + repo.remoteId();
    }

    /* ------------------------------------------------------------------------------------------ */

    private void load(
            final int current,
            final List<Account> targets
    ) {
        for (final Account account : targets) {
            final RemoteRepoJsonCodec.Snapshot cached = services.remoteRepos().cached(account.id());
            if (!cached.repos().isEmpty()) {
                deliver(current, account.id(), cached);
            }
        }
        final StringBuilder failures = new StringBuilder();
        for (final Account account : targets) {
            if (generation.get() != current) {
                return;
            }
            try {
                final RemoteRepoJsonCodec.Snapshot fresh = services.remoteRepos().refresh(account, new GitProviderClient.ListListener() {
                    @Override
                    public boolean isCancelled() {
                        return generation.get() != current;
                    }

                    @Override
                    public void onProgress(
                            final int loaded
                    ) {
                        // Die Liste erscheint erst komplett; der Balken oben zeigt, dass geladen wird.
                    }
                });
                deliver(current, account.id(), fresh);
            } catch (final CancellationException superseded) {
                return;
            } catch (final ProviderException failed) {
                Log.w(LOG_TAG, "Refresh failed: " + failed.kind() + " " + failed.getMessage());
                appendFailure(failures, account, ProviderErrorTexts.describe(getApplication(), failed));
            } catch (final VaultException unreadable) {
                appendFailure(failures, account, ProviderErrorTexts.describe(getApplication(), unreadable));
            } catch (final IOException unreadable) {
                Log.w(LOG_TAG, "Caching failed: " + unreadable);
                appendFailure(failures, account, getApplication().getString(R.string.error_network));
            } catch (final RuntimeException unexpected) {
                // Sonst bliebe der Ladebalken für immer stehen: das Ende dieses Laufs wird unten gemeldet.
                Log.w(LOG_TAG, "Refresh failed unexpectedly: " + unexpected);
                appendFailure(failures, account, getApplication().getString(R.string.error_malformed));
            }
        }
        final String message = failures.length() == 0 ? null : failures.toString();
        main.post(() -> {
            if (generation.get() == current) {
                loading = false;
                error = message;
                publish();
            }
        });
    }

    private static void appendFailure(
            final StringBuilder failures,
            final Account account,
            final String text
    ) {
        if (failures.length() > 0) {
            failures.append((char) 10);
        }
        failures.append(account.label()).append(": ").append(text);
    }

    private void deliver(
            final int current,
            final String accountId,
            final RemoteRepoJsonCodec.Snapshot snapshot
    ) {
        main.post(() -> {
            if (generation.get() == current) {
                snapshots.put(accountId, snapshot);
                publish();
            }
        });
    }

    /** Sucht in den Arbeitsordnern, welche Repos schon liegen, und merkt sich deren Remote-Adressen. */
    private void reloadClonedKeys() {
        if (!StoragePermission.isGranted()) {
            return;
        }
        services.io().execute(() -> {
            final Set<String> keys = new HashSet<>();
            for (final LocalRepo repo : services.localRepos().scan(() -> false)) {
                final Optional<RemoteUrl> origin = services.localRepos().origin(repo);
                origin.ifPresent(url -> keys.add(hostPathKey(url)));
            }
            main.post(() -> {
                clonedKeys.clear();
                clonedKeys.addAll(keys);
                publish();
            });
        });
    }

    private static String hostPathKey(
            final RemoteUrl url
    ) {
        return (url.host() + "/" + url.path()).toLowerCase(Locale.ROOT);
    }

    private boolean isCloned(
            final RemoteRepo repo
    ) {
        return repo.remoteUrl().map(url -> clonedKeys.contains(hostPathKey(url))).orElse(false);
    }

    @Nullable
    private RemoteRepo find(
            final String key
    ) {
        for (final RemoteRepoJsonCodec.Snapshot snapshot : snapshots.values()) {
            for (final RemoteRepo repo : snapshot.repos()) {
                if (keyOf(repo).equals(key)) {
                    return repo;
                }
            }
        }
        return null;
    }

    private void publish() {
        final List<AccountChip> chips = new ArrayList<>();
        if (accounts.size() > 1) {
            for (final Account account : accounts) {
                chips.add(new AccountChip(account.id(), account.label()));
            }
        }
        int total = 0;
        long oldest = 0L;
        final List<Row> rows = new ArrayList<>();
        for (final Account account : accounts) {
            final RemoteRepoJsonCodec.Snapshot snapshot = snapshots.get(account.id());
            if (snapshot == null) {
                continue;
            }
            total += snapshot.repos().size();
            if (snapshot.fetchedAtMillis() > 0L) {
                oldest = oldest == 0L ? snapshot.fetchedAtMillis() : Math.min(oldest, snapshot.fetchedAtMillis());
            }
            if (selectedAccount != null && !selectedAccount.equals(account.id())) {
                continue;
            }
            for (final RemoteRepo repo : snapshot.repos()) {
                final boolean cloned = isCloned(repo);
                if (matches(repo, cloned)) {
                    rows.add(new Row(repo, account.endpoint().provider(), cloned, selection.contains(keyOf(repo))));
                }
            }
        }
        rows.sort(sort == Sort.NAME ? BY_NAME : BY_RECENT);
        state.setValue(new UiState(loading, accounts.isEmpty(), chips, selectedAccount, filter, sort, query,
                rows, selection.size(), total, error, oldest));
    }

    private boolean matches(
            final RemoteRepo repo,
            final boolean cloned
    ) {
        switch (filter) {
            case NOT_CLONED:
                if (cloned) {
                    return false;
                }
                break;
            case PRIVATE:
                if (!repo.isPrivate()) {
                    return false;
                }
                break;
            case PUBLIC:
                if (repo.isPrivate()) {
                    return false;
                }
                break;
            case FORKS:
                if (!repo.isFork()) {
                    return false;
                }
                break;
            case ARCHIVED:
                if (!repo.isArchived()) {
                    return false;
                }
                break;
            case ALL:
            default:
                break;
        }
        if (query.isEmpty()) {
            return true;
        }
        final String needle = query.toLowerCase(Locale.ROOT);
        return repo.fullPath().toLowerCase(Locale.ROOT).contains(needle)
                || repo.description().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static final Comparator<Row> BY_NAME = Comparator
            .comparing((Row row) -> row.repo().name().toLowerCase(Locale.ROOT))
            .thenComparing(row -> row.repo().fullPath().toLowerCase(Locale.ROOT));

    private static final Comparator<Row> BY_RECENT = Comparator
            .comparingLong((Row row) -> row.repo().lastActivityMillis()).reversed()
            .thenComparing(BY_NAME);

    private static Set<String> idsOf(
            final List<Account> accounts
    ) {
        final Set<String> ids = new HashSet<>();
        for (final Account account : accounts) {
            ids.add(account.id());
        }
        return ids;
    }
}
