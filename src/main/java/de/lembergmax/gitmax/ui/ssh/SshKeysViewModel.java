package de.lembergmax.gitmax.ui.ssh;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.SshKeyInfo;
import de.lembergmax.gitmax.domain.model.SshKeyType;
import de.lembergmax.gitmax.git.ssh.SshKeyCodec;
import de.lembergmax.gitmax.git.ssh.SshKeyException;
import de.lembergmax.gitmax.ui.common.Event;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Hält die SSH-Schlüssel der App: Erzeugen, Importieren (die Datei wird gelesen und geprüft, bevor nach Namen und
 * Passphrase gefragt wird), Löschen. Zustand nur auf dem Hauptthread.
 */
public final class SshKeysViewModel extends AndroidViewModel {

    /** Größte Datei, die als Schlüssel gelesen wird. */
    private static final int MAX_KEY_BYTES = 64 * 1024;

    /**
     * Zustand des Bildschirms.
     *
     * @param loading die Liste wird gelesen
     * @param keys    die Schlüssel
     */
    public record UiState(
            boolean loading,
            @NonNull List<SshKeyInfo> keys
    ) {
    }

    /**
     * Eine gelesene Schlüsseldatei wartet auf Namen und gegebenenfalls Passphrase.
     *
     * @param suggestedName     Vorschlag für den Namen (Dateiname ohne Endung)
     * @param needsPassphrase   der Schlüssel ist geschützt
     */
    public record ImportRequest(
            @NonNull String suggestedName,
            boolean needsPassphrase
    ) {
    }

    /**
     * Einmalige Meldung.
     *
     * @param text  Text
     * @param error ein Fehler, kein Erfolg
     */
    public record Notice(
            @NonNull String text,
            boolean error
    ) {
    }

    private final ServiceLocator services;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<UiState> state = new MutableLiveData<>(new UiState(true, List.of()));
    private final MutableLiveData<Event<Notice>> notices = new MutableLiveData<>();
    private final MutableLiveData<Event<ImportRequest>> importRequests = new MutableLiveData<>();

    private String pendingText;
    private int generation;

    public SshKeysViewModel(
            @NonNull final Application application
    ) {
        super(application);
        this.services = ServiceLocator.from(application);
        reload();
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }

    @NonNull
    public LiveData<Event<Notice>> notices() {
        return notices;
    }

    /** Eine gelesene Schlüsseldatei, die noch Name und Passphrase braucht. */
    @NonNull
    public LiveData<Event<ImportRequest>> importRequests() {
        return importRequests;
    }

    public void reload() {
        generation += 1;
        final int current = generation;
        services.io().execute(() -> {
            final List<SshKeyInfo> keys = services.sshKeys().list();
            main.post(() -> {
                if (current == generation) {
                    state.setValue(new UiState(false, keys));
                }
            });
        });
    }

    /** Die verknüpften Konten, bei denen sich der Schlüssel hinterlegen lässt. */
    @NonNull
    public List<Account> accounts() {
        return new ArrayList<>(services.accounts().all());
    }

    public void generate(
            @NonNull final String label,
            @NonNull final SshKeyType type
    ) {
        services.io().execute(() -> {
            Notice result;
            try {
                services.sshKeys().generate(label, type);
                result = new Notice(getApplication().getString(R.string.ssh_key_created), false);
            } catch (final SshKeyException failed) {
                result = new Notice(describe(failed), true);
            }
            finish(result);
        });
    }

    /** Liest die gewählte Datei und prüft, ob sie ein Schlüssel ist; danach fragt die Oberfläche nach Namen und Passphrase. */
    public void prepareImport(
            @NonNull final Uri uri,
            @NonNull final String fileName
    ) {
        services.io().execute(() -> {
            try {
                final String text = read(uri);
                boolean needsPassphrase = false;
                try {
                    SshKeyCodec.decodePrivate(text, null);
                } catch (final SshKeyException unreadable) {
                    if (unreadable.reason() != SshKeyException.Reason.PASSPHRASE_REQUIRED) {
                        throw unreadable;
                    }
                    needsPassphrase = true;
                }
                final boolean protectedKey = needsPassphrase;
                main.post(() -> {
                    pendingText = text;
                    importRequests.setValue(new Event<>(new ImportRequest(baseName(fileName), protectedKey)));
                });
            } catch (final SshKeyException failed) {
                finish(new Notice(describe(failed), true));
            } catch (final IOException unreadable) {
                finish(new Notice(getApplication().getString(R.string.ssh_error_read), true));
            }
        });
    }

    public void importPending(
            @NonNull final String label,
            @Nullable final String passphrase
    ) {
        final String text = pendingText;
        if (text == null) {
            return;
        }
        services.io().execute(() -> {
            Notice result;
            try {
                services.sshKeys().importKey(label, text, passphrase);
                main.post(() -> pendingText = null);
                result = new Notice(getApplication().getString(R.string.ssh_key_imported), false);
            } catch (final SshKeyException failed) {
                result = new Notice(describe(failed), true);
            }
            finish(result);
        });
    }

    public void remove(
            @NonNull final SshKeyInfo key
    ) {
        services.io().execute(() -> {
            services.sshKeys().remove(key.id());
            finish(new Notice(getApplication().getString(R.string.ssh_key_deleted), false));
        });
    }

    private void finish(
            @NonNull final Notice result
    ) {
        main.post(() -> {
            notices.setValue(new Event<>(result));
            reload();
        });
    }

    private String read(
            final Uri uri
    ) throws IOException, SshKeyException {
        try (InputStream in = getApplication().getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("File unreadable");
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8 * 1024];
            int read = in.read(buffer);
            while (read >= 0) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_KEY_BYTES) {
                    throw new SshKeyException(SshKeyException.Reason.TOO_LARGE, "The file is too large", null);
                }
                read = in.read(buffer);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static String baseName(
            final String fileName
    ) {
        final int dot = fileName.lastIndexOf('.');
        final String name = dot > 0 ? fileName.substring(0, dot) : fileName;
        return name.length() > 60 ? name.substring(0, 60) : name;
    }

    @StringRes
    private static int textFor(
            final SshKeyException.Reason reason
    ) {
        switch (reason) {
            case PASSPHRASE_REQUIRED:
                return R.string.ssh_error_passphrase_required;
            case WRONG_PASSPHRASE:
                return R.string.ssh_error_wrong_passphrase;
            case UNSUPPORTED:
                return R.string.ssh_error_unsupported;
            case DUPLICATE:
                return R.string.ssh_error_duplicate;
            case INVALID_NAME:
                return R.string.ssh_error_name;
            case TOO_LARGE:
                return R.string.ssh_error_too_large;
            case FAILED:
                return R.string.ssh_error_failed;
            case INVALID:
            default:
                return R.string.ssh_error_invalid;
        }
    }

    private String describe(
            final SshKeyException failed
    ) {
        return getApplication().getString(textFor(failed.reason()));
    }
}
