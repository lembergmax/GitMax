package de.lembergmax.gitmax.ui.common;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

/** Öffnet oder teilt eine Datei mit anderen Apps; die App bekommt das Leserecht nur für diese eine Datei. */
public final class ExternalFiles {

    private static final String AUTHORITY_SUFFIX = ".files";
    private static final String FALLBACK_TYPE = "text/plain";

    private ExternalFiles() {
    }

    /**
     * Öffnet die Datei in einer anderen App.
     *
     * @return {@code false}, wenn keine App sie öffnen kann
     */
    public static boolean open(
            @NonNull final Context context,
            @NonNull final File file
    ) {
        final Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uriFor(context, file), mimeTypeOf(file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return start(context, Intent.createChooser(intent, null));
    }

    /**
     * Teilt die Datei.
     *
     * @return {@code false}, wenn keine App sie annehmen kann
     */
    public static boolean share(
            @NonNull final Context context,
            @NonNull final File file
    ) {
        final Intent intent = new Intent(Intent.ACTION_SEND)
                .setType(mimeTypeOf(file))
                .putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return start(context, Intent.createChooser(intent, null));
    }

    private static Uri uriFor(
            final Context context,
            final File file
    ) {
        Objects.requireNonNull(file, "file");
        return FileProvider.getUriForFile(context, context.getPackageName() + AUTHORITY_SUFFIX, file);
    }

    private static boolean start(
            final Context context,
            final Intent intent
    ) {
        try {
            context.startActivity(intent);
            return true;
        } catch (final ActivityNotFoundException noApp) {
            return false;
        }
    }

    /** Der Medientyp aus der Dateiendung, sonst Text (die meisten Dateien in Repos sind Text). */
    @NonNull
    public static String mimeTypeOf(
            @NonNull final File file
    ) {
        final String name = file.getName();
        final int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return FALLBACK_TYPE;
        }
        final String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
        return type == null ? FALLBACK_TYPE : type;
    }
}
