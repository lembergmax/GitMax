import java.util.Properties

// Single-Modul-Projekt (wie RechenMax): Das Application-Plugin wird auf das Wurzelprojekt angewendet,
// es gibt kein ":app"-Unterprojekt, Gradle-Aufgaben sind unpräfixiert (./gradlew assembleDebug) und
// die Quellen liegen unter src/ im Wurzelverzeichnis.
plugins {
    alias(libs.plugins.android.application)
}

// Release-Signierung wird aus local.properties (nicht eingecheckt) oder Umgebungsvariablen
// gelesen, damit ein sauberer Checkout / CI ohne im IDE-Zustand liegenden Keystore signieren kann.
// Setze RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}
fun secret(name: String): String? = keystoreProperties.getProperty(name) ?: System.getenv(name)
val hasReleaseSigning = secret("RELEASE_STORE_FILE") != null

android {
    namespace = "de.lembergmax.gitmax"
    compileSdk = 36
    // Fest auf eine Version, die im SDK von Android Studio (D:\Android\Sdk) liegt, damit die
    // Synchronisierung nicht erst eine andere Build-Tools-Version nachladen muss.
    buildToolsVersion = "36.1.0"

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(secret("RELEASE_STORE_FILE")!!)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "de.lembergmax.gitmax"
        // 34 statt 29 wie bei den Schwesterprojekten: JGit 6/7 nutzt Java-API, die erst die
        // Java-17-Klassenbibliothek ab Android 14 mitbringt (z. B. InputStream.readNBytes → API 33).
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // TEST_BUILD lockert zwei Schutzmaßnahmen, die für Prüfläufe auf dem Emulator im Weg sind: Klartext-HTTP zu
        // Emulator-Adressen (Fake-Server) und FLAG_SECURE (Screenshots). Nur debug und minified setzen es; der
        // Release-Build hat es aus.
        debug {
            buildConfigField("boolean", "TEST_BUILD", "true")
        }
        release {
            buildConfigField("boolean", "TEST_BUILD", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            // Signierkonfiguration nur setzen, wenn ein Keystore konfiguriert ist – sonst bleibt
            // der Release-Build unsigniert (z. B. CI-Rauchtest), statt an einer fehlenden storeFile
            // zu scheitern.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        // Wie "release" (R8 an), aber debug-signiert, damit die Instrumentierungstests gegen die
        // geschrumpfte und obfuskierte App laufen: ./gradlew -PtestBuildType=minified connectedAndroidTest
        // Bewusst NICHT debuggbar: sonst schaltet R8 Optimierung und Obfuskation ab, und der Test
        // prüfte nicht mehr, was er prüfen soll. Die Tests brauchen Zugriff auf App-Klassen, deshalb
        // liegt eine zusätzliche Keep-Regel an.
        create("minified") {
            initWith(getByName("release"))
            buildConfigField("boolean", "TEST_BUILD", "true")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-test-rules.pro"
            )
        }
    }

    // Fixtures für Gerätetests (src/testSupport): Die JGit-Aufrufe gehören in die App, damit R8 im Build-Typ
    // minified sie zusammen mit der Engine sieht. Nur in debug und minified, nie im Release.
    sourceSets {
        getByName("debug") { java.srcDir("src/testSupport/java") }
        getByName("minified") { java.srcDir("src/testSupport/java") }
    }

    testBuildType = providers.gradleProperty("testBuildType").getOrElse("debug")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    androidResources {
        // GitMax spricht Englisch (Standard) und Deutsch; die übrigen Übersetzungen der Bibliotheken bleiben draußen.
        localeFilters += listOf("en", "de")
    }

    lint {
        // Fehler brechen den Build ab; abgeschaltet ist nur, was für dieses Projekt nichts aussagt:
        disable += setOf(
            // Deutsche Texte werden gegen englische Wörterbücher geprüft („Committen“, „Autor“).
            "Typos",
            // Versionen sind bewusst festgelegt und geprüft; Aktualisierungen entscheidet Max.
            "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion",
            // Die verbliebenen Zahlentexte sind Wendungen („3 ausgewählt“, „2 voraus“), keine Substantive mit Zähler.
            "PluralsCandidate",
            // Fragment-Wurzeln malen den Hintergrund selbst: Material-Übergänge überblenden sonst die Seiten.
            "Overdraw",
            // Stammt aus JGit (nur für http.sslVerify=false); GitMax setzt das nie und isoliert die Git-Konfiguration.
            "TrustAllX509TrustManager"
        )
    }

    packaging {
        resources {
            // JGit und Apache MINA SSHD bringen gleichnamige Metadaten mit; sie werden zur Laufzeit
            // nicht gelesen. META-INF/services/** wird von AGP standardmäßig zusammengeführt.
            excludes += setOf(
                "/META-INF/AL2.0",
                "/META-INF/LGPL2.1",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/INDEX.LIST",
                "/META-INF/*.kotlin_module",
                "/META-INF/versions/**",
                "**/module-info.class",
                "/about.html",
                "/plugin.properties",
                // OSGi-Metadaten (Übersetzungen der Eclipse-Plugin-Beschreibung), nur in Eclipse nutzbar.
                // Die Meldungstexte von JGit (org/eclipse/jgit/internal/JGitText*.properties) bleiben.
                "/OSGI-INF/**"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)

    implementation(libs.jgit)
    implementation(libs.jgit.ssh.apache)
    // Ed25519 für MINA SSHD: die Android-Plattform bringt es nicht verlässlich mit (JDK 15+ schon, Android nicht).
    implementation(libs.eddsa)
    implementation(libs.slf4j.android)

    testImplementation(libs.junit)
    // LFS-Spike (M10, Entscheidung: LFS bleibt aus): nur im Testpfad, damit LfsSpikeTest den Befund prüfbar hält.
    // Das Modul (und gson) gehört bewusst nicht in die App.
    testImplementation(libs.jgit.lfs)
    // org.json ist auf dem Android-Gerät Teil der Plattform, in JVM-Unit-Tests aber nur ein Stub
    // (mit unitTests.isReturnDefaultValues = true liefert jeder Aufruf still einen Default statt zu
    // parsen). Das legt eine echte Implementierung ausschließlich auf den Unit-Test-Klassenpfad –
    // die App selbst nutzt weiter die Plattform-Kopie.
    testImplementation(libs.json)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
