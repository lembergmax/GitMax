package de.lembergmax.gitmax.ui.common;

import android.content.Context;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.provider.ProviderException;
import de.lembergmax.gitmax.storage.VaultException;

/**
 * Macht aus Fehlern der Anbieter-API und des Tresors Sätze, die Problem und nächsten Schritt nennen.
 */
public final class ProviderErrorTexts {

    private static final long SECONDS_PER_MINUTE = 60L;

    private ProviderErrorTexts() {
    }

    @NonNull
    public static String describe(
            @NonNull final Context context,
            @NonNull final ProviderException failure
    ) {
        switch (failure.kind()) {
            case UNAUTHORIZED:
                return context.getString(R.string.error_unauthorized);
            case FORBIDDEN:
                return context.getString(R.string.error_forbidden);
            case RATE_LIMITED:
                if (failure.retryAfterSeconds() <= 0L) {
                    return context.getString(R.string.error_rate_limited_unknown);
                }
                final long minutes = (failure.retryAfterSeconds() + SECONDS_PER_MINUTE - 1L) / SECONDS_PER_MINUTE;
                return context.getResources().getQuantityString(R.plurals.error_rate_limited, (int) minutes, (int) minutes);
            case NOT_FOUND:
                return context.getString(R.string.error_not_found);
            case NETWORK:
                return context.getString(R.string.error_network);
            case SERVER:
                return context.getString(R.string.error_server);
            case MALFORMED:
            default:
                return context.getString(R.string.error_malformed);
        }
    }

    @NonNull
    public static String describe(
            @NonNull final Context context,
            @NonNull final VaultException failure
    ) {
        return context.getString(R.string.error_vault);
    }
}
