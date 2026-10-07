package de.lembergmax.gitmax.ui.settings;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.BuildConfig;
import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.databinding.FragmentSettingsBinding;
import de.lembergmax.gitmax.storage.AppSettings;
import de.lembergmax.gitmax.ui.common.Appearance;
import de.lembergmax.gitmax.ui.common.Languages;
import de.lembergmax.gitmax.ui.common.Motion;

/**
 * Einstieg in die Einstellungen. Wächst mit den Meilensteinen: Konten, Arbeitsordner, Commit-Identität,
 * Update-Strategie, SSH-Schlüssel und weitere.
 */
public final class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private AppSettings settings;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onCreate(
            @Nullable final Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        setEnterTransition(Motion.fadeThrough(requireContext()));
        setExitTransition(Motion.fadeThrough(requireContext()));
        setReenterTransition(Motion.fadeThrough(requireContext()));
        setReturnTransition(Motion.fadeThrough(requireContext()));
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentSettingsBinding.bind(view);

        binding.rowWorkspace.icon.setImageResource(R.drawable.ic_folder);
        binding.rowWorkspace.title.setText(R.string.workspace_title);
        binding.rowWorkspace.getRoot().setOnClickListener(row -> openDeeper(R.id.workspaceFragment));

        binding.rowAccounts.icon.setImageResource(R.drawable.ic_manage_accounts);
        binding.rowAccounts.title.setText(R.string.settings_accounts);
        binding.rowAccounts.getRoot().setOnClickListener(row -> openDeeper(R.id.accountsFragment));

        binding.rowSsh.icon.setImageResource(R.drawable.ic_key);
        binding.rowSsh.title.setText(R.string.ssh_keys_title);
        binding.rowSsh.getRoot().setOnClickListener(row -> openDeeper(R.id.sshKeysFragment));

        settings = ServiceLocator.from(requireContext()).settings();
        binding.rowTheme.icon.setImageResource(R.drawable.ic_dark_mode);
        binding.rowTheme.title.setText(R.string.settings_theme_title);
        binding.rowTheme.summary.setText(themeLabel(settings.themeMode()));
        binding.rowTheme.getRoot().setOnClickListener(row -> chooseTheme());

        binding.rowLanguage.icon.setImageResource(R.drawable.ic_translate);
        binding.rowLanguage.title.setText(R.string.settings_language_title);
        binding.rowLanguage.summary.setText(languageLabel(Languages.current()));
        binding.rowLanguage.getRoot().setOnClickListener(row -> chooseLanguage());

        final boolean colorsAvailable = Appearance.systemColorsAvailable();
        binding.switchSystemColors.setEnabled(colorsAvailable);
        binding.switchSystemColors.setChecked(colorsAvailable && settings.systemColors());
        binding.hintSystemColors.setText(colorsAvailable ? R.string.settings_system_colors_hint
                : R.string.settings_system_colors_unavailable);
        binding.switchSystemColors.setOnCheckedChangeListener((button, checked) -> {
            final Activity activity = requireActivity();
            // Erst nach dem Speichern neu aufbauen: Die neue Activity liest die Einstellung beim Erzeugen, und ein
            // gleichzeitiger Schreibvorgang im Hintergrund könnte noch nicht fertig sein.
            saveInBackground(() -> {
                settings.setSystemColors(checked);
                // Das Theme wird beim Erzeugen der Activity gesetzt; neu aufbauen, damit die Farben wirken.
                activity.runOnUiThread(activity::recreate);
            });
        });

        binding.switchWifiOnly.setChecked(settings.wifiOnly());
        binding.switchWifiOnly.setOnCheckedChangeListener((button, checked) ->
                saveInBackground(() -> settings.setWifiOnly(checked)));

        binding.rowAbout.icon.setImageResource(R.drawable.ic_description);
        binding.rowAbout.title.setText(R.string.about_title);
        binding.rowAbout.summary.setText(getString(R.string.settings_about_summary, BuildConfig.VERSION_NAME));
        binding.rowAbout.getRoot().setOnClickListener(row -> openDeeper(R.id.aboutFragment));
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSummaries();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    /**
     * Beim Wechsel in die Tiefe gleitet der Inhalt entlang der Achse statt zu überblenden; beim
     * Zurückkehren umgekehrt. Beim Wechsel zwischen den Hauptzielen bleibt es bei der Überblendung.
     */
    private void openDeeper(
            final int destinationId
    ) {
        setExitTransition(Motion.sharedAxis(requireContext(), MaterialSharedAxis.X, true));
        setReenterTransition(Motion.sharedAxis(requireContext(), MaterialSharedAxis.X, false));
        NavHostFragment.findNavController(this).navigate(destinationId);
    }

    private void chooseTheme() {
        final AppSettings.ThemeMode[] modes = AppSettings.ThemeMode.values();
        final String[] labels = new String[modes.length];
        int checked = 0;
        for (int index = 0; index < modes.length; index += 1) {
            labels[index] = themeLabel(modes[index]);
            if (modes[index] == settings.themeMode()) {
                checked = index;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_theme_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    final AppSettings.ThemeMode mode = modes[which];
                    saveInBackground(() -> settings.setThemeMode(mode));
                    binding.rowTheme.summary.setText(themeLabel(mode));
                    dialog.dismiss();
                    Appearance.applyNightMode(mode);
                })
                .setNegativeButton(R.string.activity_close, null)
                .show();
    }

    private void chooseLanguage() {
        final Languages.Choice[] choices = Languages.Choice.values();
        final String[] labels = new String[choices.length];
        int checked = 0;
        for (int index = 0; index < choices.length; index += 1) {
            labels[index] = languageLabel(choices[index]);
            if (choices[index] == Languages.current()) {
                checked = index;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_language_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    // Android baut die offene Activity mit der neuen Sprache neu auf.
                    Languages.apply(choices[which]);
                })
                .setNegativeButton(R.string.activity_close, null)
                .show();
    }

    private String languageLabel(
            @NonNull final Languages.Choice choice
    ) {
        switch (choice) {
            case ENGLISH:
                return getString(R.string.settings_language_english);
            case GERMAN:
                return getString(R.string.settings_language_german);
            case SYSTEM:
            default:
                return getString(R.string.settings_language_system);
        }
    }

    private String themeLabel(
            @NonNull final AppSettings.ThemeMode mode
    ) {
        switch (mode) {
            case LIGHT:
                return getString(R.string.settings_theme_light);
            case DARK:
                return getString(R.string.settings_theme_dark);
            case SYSTEM:
            default:
                return getString(R.string.settings_theme_system);
        }
    }

    /** Schreibt eine Einstellung abseits des Hauptthreads; der Wert wirkt sofort, das Speichern folgt. */
    private void saveInBackground(
            @NonNull final Runnable save
    ) {
        ServiceLocator.from(requireContext()).io().execute(save);
    }

    private void updateSummaries() {
        final ServiceLocator services = ServiceLocator.from(requireContext());
        services.io().execute(() -> {
            final int accounts = services.accounts().all().size();
            final int roots = services.workspace().roots().size();
            final int sshKeys = services.sshKeys().list().size();
            final View root = getView();
            if (root != null) {
                root.post(() -> showSummaries(accounts, roots, sshKeys));
            }
        });
    }

    private void showSummaries(
            final int accounts,
            final int roots,
            final int sshKeys
    ) {
        if (binding == null) {
            return;
        }
        binding.rowAccounts.summary.setText(accounts == 0
                ? getString(R.string.settings_accounts_none)
                : getResources().getQuantityString(R.plurals.settings_accounts_count, accounts, accounts));
        binding.rowSsh.summary.setText(sshKeys == 0
                ? getString(R.string.settings_ssh_none)
                : getResources().getQuantityString(R.plurals.settings_ssh_count, sshKeys, sshKeys));
        binding.rowWorkspace.summary.setText(roots == 0
                ? getString(R.string.workspace_summary_none)
                : getResources().getQuantityString(R.plurals.workspace_summary_count, roots, roots));
    }
}
