package de.lembergmax.gitmax.ops;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.storage.AppSettings;

import java.util.Objects;

/**
 * Setzt „Nur im WLAN“ um: Ist die Einstellung an, sind nur ungemessene Netze zulässig. Ohne Netz gibt es nichts zu
 * schützen, der Vorgang scheitert dann ohnehin mit der gewohnten Meldung „Keine Verbindung“.
 */
public final class AndroidNetworkPolicy implements NetworkPolicy {

    private final ConnectivityManager connectivity;
    private final AppSettings settings;

    public AndroidNetworkPolicy(
            @NonNull final Context context,
            @NonNull final AppSettings settings
    ) {
        Objects.requireNonNull(context, "context");
        this.connectivity = Objects.requireNonNull(context.getSystemService(ConnectivityManager.class), "connectivity");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public boolean allowsNow() {
        if (!settings.wifiOnly()) {
            return true;
        }
        final Network active = connectivity.getActiveNetwork();
        final NetworkCapabilities capabilities = active == null ? null : connectivity.getNetworkCapabilities(active);
        return capabilities == null || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }
}
