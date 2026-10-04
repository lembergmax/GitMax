# Nur für den Build-Typ "minified": Die Instrumentierungstests (separates APK) greifen auf App-Klassen
# zu. Geprüft werden soll, ob JGit und MINA SSHD das R8-Schrumpfen überstehen, nicht die App-Klassen.
-keep class de.lembergmax.gitmax.** { *; }

# Das Test-APK lässt Bibliotheksklassen weg, die schon in der App liegen. Entfernt R8 sie aus der App,
# findet der Test-Runner sie nicht mehr (NoClassDefFoundError beim Start des AndroidJUnitRunner).
-keep class androidx.tracing.** { *; }
-keep class androidx.test.** { *; }
-keep class androidx.annotation.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class org.jetbrains.annotations.** { *; }
-keep class com.google.common.util.concurrent.** { *; }
