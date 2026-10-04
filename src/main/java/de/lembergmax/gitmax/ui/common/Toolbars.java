package de.lembergmax.gitmax.ui.common;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.appbar.MaterialToolbar;

import de.lembergmax.gitmax.R;

/**
 * Gemeinsame Einrichtung der Kopfleisten.
 */
public final class Toolbars {

    private Toolbars() {
    }

    /** Zeigt den Zurück-Pfeil und führt eine Ebene nach oben (wie die System-Zurück-Geste). */
    public static void setupBack(
            @NonNull final Fragment fragment,
            @NonNull final MaterialToolbar toolbar
    ) {
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription(R.string.navigate_back);
        toolbar.setNavigationOnClickListener(view -> NavHostFragment.findNavController(fragment).navigateUp());
    }
}
