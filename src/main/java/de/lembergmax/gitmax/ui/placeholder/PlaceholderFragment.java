package de.lembergmax.gitmax.ui.placeholder;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.FragmentPlaceholderBinding;
import de.lembergmax.gitmax.ui.common.Motion;

/**
 * Platzhalter für Bereiche, die in späteren Meilensteinen (M2 bis M5) gebaut werden. Zeigt Titel und
 * einen kurzen Hinweis und trägt schon die Übergänge zwischen den Navigationszielen.
 */
public final class PlaceholderFragment extends Fragment {

    private static final String ARG_TITLE = "title";

    public PlaceholderFragment() {
        super(R.layout.fragment_placeholder);
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
        final FragmentPlaceholderBinding binding = FragmentPlaceholderBinding.bind(view);
        final Bundle arguments = getArguments();
        final int titleRes = arguments != null && arguments.containsKey(ARG_TITLE)
                ? arguments.getInt(ARG_TITLE)
                : R.string.app_name;
        binding.toolbar.setTitle(titleRes);
    }
}
