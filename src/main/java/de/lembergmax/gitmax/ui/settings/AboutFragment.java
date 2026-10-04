package de.lembergmax.gitmax.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.BuildConfig;
import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.DialogLicenseBinding;
import de.lembergmax.gitmax.databinding.FragmentAboutBinding;
import de.lembergmax.gitmax.databinding.ItemSettingBinding;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Über GitMax: Version, Hinweis zum Datenschutz und die Lizenzen der verwendeten fremden Software. */
public final class AboutFragment extends Fragment {

    private FragmentAboutBinding binding;

    public AboutFragment() {
        super(R.layout.fragment_about);
    }

    @Override
    public void onCreate(
            @Nullable final Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        setEnterTransition(Motion.sharedAxis(requireContext(), MaterialSharedAxis.X, true));
        setReturnTransition(Motion.sharedAxis(requireContext(), MaterialSharedAxis.X, false));
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentAboutBinding.bind(view);
        Toolbars.setupBack(this, binding.toolbar);
        binding.version.setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));

        final LayoutInflater inflater = getLayoutInflater();
        for (final LicenseCatalog.Entry entry : LicenseCatalog.entries()) {
            final ItemSettingBinding row = ItemSettingBinding.inflate(inflater, binding.licenses, false);
            row.icon.setImageResource(R.drawable.ic_description);
            row.title.setText(entry.name());
            row.summary.setText(entry.detail());
            row.getRoot().setOnClickListener(clicked -> showLicense(entry));
            binding.licenses.addView(row.getRoot());
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void showLicense(
            @NonNull final LicenseCatalog.Entry entry
    ) {
        final DialogLicenseBinding dialog = DialogLicenseBinding.inflate(getLayoutInflater());
        dialog.licenseText.setText(readText(entry.text()));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(entry.name())
                .setView(dialog.getRoot())
                .setPositiveButton(R.string.activity_close, null)
                .show();
    }

    /** Die Lizenztexte sind klein (höchstens etwa 12 KB) und liegen in der APK; Lesen auf dem Hauptthread ist unkritisch. */
    @NonNull
    private String readText(
            @RawRes final int rawResource
    ) {
        try (InputStream stream = getResources().openRawResource(rawResource)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException unreadable) {
            return getString(R.string.about_license_unreadable);
        }
    }
}
