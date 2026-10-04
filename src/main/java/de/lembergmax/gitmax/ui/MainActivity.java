package de.lembergmax.gitmax.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationBarView;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.ShareTarget;
import de.lembergmax.gitmax.ui.common.Appearance;
import de.lembergmax.gitmax.ui.discover.DiscoverFragment;

import java.util.Optional;

/**
 * Die einzige Activity der App: hält die Navigationsleiste (kompakt unten, breit als Rail) und den
 * Navigationsbereich für die Fragmente.
 */
public final class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(
            @Nullable final Bundle savedInstanceState
    ) {
        SplashScreen.installSplashScreen(this);
        Appearance.applySystemColors(this, ServiceLocator.from(this).settings());
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        final NavController navController = findNavController();
        final NavigationBarView navigation = findViewById(R.id.navigation);
        NavigationUI.setupWithNavController(navigation, navController);
        // Unterseiten (Konto verbinden, Detail …) haben einen Zurück-Pfeil und brauchen die Leiste nicht.
        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                navigation.setVisibility(isTopLevel(destination.getId()) ? View.VISIBLE : View.GONE));
        if (savedInstanceState == null) {
            handleShare(getIntent());
        }
    }

    @Override
    protected void onNewIntent(
            @NonNull final Intent intent
    ) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent);
    }

    /** Nimmt geteilten Text entgegen und öffnet „Entdecken“ mit dem Klon-Sheet, wenn er eine brauchbare Adresse enthält. */
    private void handleShare(
            @Nullable final Intent intent
    ) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction()) || !"text/plain".equals(intent.getType())) {
            return;
        }
        final CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        final Optional<RemoteUrl> url = ShareTarget.extract(text == null ? null : text.toString());
        // Der Auftrag ist gelesen; eine Drehung oder ein Neustart der Activity soll ihn nicht wiederholen.
        intent.setAction(Intent.ACTION_MAIN);
        if (url.isEmpty()) {
            Toast.makeText(this, R.string.share_no_url, Toast.LENGTH_LONG).show();
            return;
        }
        final Bundle arguments = new Bundle();
        arguments.putString(DiscoverFragment.ARG_CLONE_URL, url.get().toHttpsUrl());
        final NavController navController = findNavController();
        navController.navigate(R.id.discoverFragment, arguments, new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false)
                .build());
    }

    private static boolean isTopLevel(
            final int destinationId
    ) {
        return destinationId == R.id.localFragment
                || destinationId == R.id.discoverFragment
                || destinationId == R.id.activityFragment
                || destinationId == R.id.settingsFragment;
    }

    private NavController findNavController() {
        final Fragment host = getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (!(host instanceof NavHostFragment navHost)) {
            throw new IllegalStateException("Navigation host not found");
        }
        return navHost.getNavController();
    }
}
