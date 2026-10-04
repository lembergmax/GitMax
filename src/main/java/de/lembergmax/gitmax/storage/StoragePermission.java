package de.lembergmax.gitmax.storage;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Objects;

/**
 * Die Sondererlaubnis „Zugriff auf alle Dateien“. JGit arbeitet auf echten Dateipfaden; ohne sie
 * kann GitMax in einem öffentlichen Ordner weder klonen noch lesen. Die Erlaubnis lässt sich nur in
 * den Systemeinstellungen erteilen.
 */
public final class StoragePermission {

    private StoragePermission() {
    }

    /** {@code true}, wenn GitMax auf alle Dateien zugreifen darf. */
    public static boolean isGranted() {
        return Environment.isExternalStorageManager();
    }

    /** Öffnet die Systemeinstellung, in der die Erlaubnis für GitMax erteilt wird. */
    @NonNull
    public static Intent settingsIntent(
            @NonNull final Context context
    ) {
        Objects.requireNonNull(context, "context");
        return new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + context.getPackageName())
        );
    }

    /** Der Telefonspeicher, in dem öffentliche Ordner liegen ({@code /storage/emulated/0}). */
    @NonNull
    public static File primaryStorageRoot() {
        return Environment.getExternalStorageDirectory();
    }
}
