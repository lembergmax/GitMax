package de.lembergmax.gitmax.domain.model;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Ein SSH-Server hat einen Schlüssel vorgelegt, dem GitMax (noch) nicht vertraut. Der Vorgang ist
 * gescheitert; die Oberfläche zeigt den Fingerabdruck und lässt den Nutzer entscheiden.
 *
 * @param host                 Kennung des Servers: {@code github.com} oder {@code [host]:port}
 * @param keyType              Name der Schlüsselart im SSH-Protokoll
 * @param keyBase64            vorgelegter öffentlicher Schlüssel, Base64
 * @param fingerprint          Fingerabdruck {@code SHA256:…} des vorgelegten Schlüssels
 * @param knownFingerprints    Fingerabdrücke, die bisher für diesen Server galten; leer bei einem neuen Server,
 *                             sonst hat sich der Schlüssel geändert
 * @param contradictsPublished der Anbieter veröffentlicht Fingerabdrücke für diesen Server, und der vorgelegte
 *                             ist keiner davon
 */
public record HostKeyChallenge(
        @NonNull String host,
        @NonNull String keyType,
        @NonNull String keyBase64,
        @NonNull String fingerprint,
        @NonNull List<String> knownFingerprints,
        boolean contradictsPublished
) {

    public HostKeyChallenge {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(keyType, "keyType");
        Objects.requireNonNull(keyBase64, "keyBase64");
        Objects.requireNonNull(fingerprint, "fingerprint");
        knownFingerprints = List.copyOf(Objects.requireNonNull(knownFingerprints, "knownFingerprints"));
    }

    /** {@code true}, wenn der Server schon bekannt war und nun einen anderen Schlüssel vorlegt. */
    public boolean changed() {
        return !knownFingerprints.isEmpty();
    }
}
