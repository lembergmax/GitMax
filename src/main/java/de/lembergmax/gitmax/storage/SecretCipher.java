package de.lembergmax.gitmax.storage;

import androidx.annotation.NonNull;

/**
 * Verschlüsselt und entschlüsselt kleine Geheimnisse. Die Trennung vom Android-Keystore erlaubt es,
 * die Logik des {@link SecretVault} in JVM-Tests mit einer anderen Chiffre zu prüfen.
 */
public interface SecretCipher {

    /**
     * @param plain Klartext
     * @param aad   zusätzlich authentifizierte Daten (der Schlüsselname): ein Chiffretext lässt sich so
     *              nicht unter einem anderen Namen wiederverwenden
     * @return Initialisierungsvektor gefolgt vom Chiffretext
     */
    @NonNull
    byte[] encrypt(
            @NonNull byte[] plain,
            @NonNull byte[] aad
    ) throws VaultException;

    /**
     * @param sealed Ergebnis von {@link #encrypt(byte[], byte[])}
     * @param aad    dieselben zusätzlichen Daten wie beim Verschlüsseln
     */
    @NonNull
    byte[] decrypt(
            @NonNull byte[] sealed,
            @NonNull byte[] aad
    ) throws VaultException;
}
