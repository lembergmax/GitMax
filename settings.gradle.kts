pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Single-Modul-Projekt wie RechenMax: Das Android-Application-Plugin wird in build.gradle.kts auf das
// Wurzelprojekt selbst angewendet, deshalb kein include(...) und kein ":app"-Präfix bei Gradle-Aufgaben.
rootProject.name = "GitMax"
