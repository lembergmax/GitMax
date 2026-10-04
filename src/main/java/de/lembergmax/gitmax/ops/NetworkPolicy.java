package de.lembergmax.gitmax.ops;

/**
 * Entscheidet, ob ein Vorgang jetzt das Netz nutzen darf (Einstellung „Nur im WLAN“). Frei von Android-Klassen, damit
 * die Warteschlange mit einem Ersatz getestet werden kann.
 */
public interface NetworkPolicy {

    /** Keine Einschränkung. */
    NetworkPolicy ALWAYS = () -> true;

    /** {@code true}, wenn das aktuelle Netz für Vorgänge zulässig ist. */
    boolean allowsNow();
}
