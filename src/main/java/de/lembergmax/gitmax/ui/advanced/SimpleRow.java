package de.lembergmax.gitmax.ui.advanced;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Eine Zeile der einfachen Listen (Branches, Stash, Tags, Remotes, Erweitert-Menü).
 *
 * @param id         stabile Kennung innerhalb der Liste
 * @param title      Haupttext
 * @param subtitle   Zusatztext, leer wenn keiner
 * @param icon       Symbol vorn
 * @param section    Überschrift der Gruppe, zu der die Zeile gehört, oder {@code null}
 * @param emphasized hervorgehoben, z. B. der aktuelle Branch
 * @param payload    Daten der Quelle zu dieser Zeile
 */
public record SimpleRow(
        @NonNull String id,
        @NonNull String title,
        @NonNull String subtitle,
        @DrawableRes int icon,
        @Nullable String section,
        boolean emphasized,
        @Nullable Object payload
) {

    public SimpleRow {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(subtitle, "subtitle");
    }
}
