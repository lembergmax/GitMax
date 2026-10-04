package de.lembergmax.gitmax;

import android.app.Application;

import de.lembergmax.gitmax.git.JgitEnvironment;
import de.lembergmax.gitmax.ui.common.Appearance;

/**
 * Einstiegspunkt der App. Richtet vor jeder Git-Nutzung die JGit-Umgebung ein und baut die
 * gemeinsam genutzten Bausteine.
 */
public final class GitMaxApplication extends Application {

    private ServiceLocator services;

    @Override
    public void onCreate() {
        super.onCreate();
        JgitEnvironment.install(this);
        services = new ServiceLocator(this);
        Appearance.applyNightMode(services.settings().themeMode());
        // Vor dem ersten neuen Vorgang aufräumen, damit kein halber Klon einem neuen im Weg steht.
        services.io().execute(services::cleanUpInterruptedClones);
    }

    ServiceLocator services() {
        return services;
    }
}
