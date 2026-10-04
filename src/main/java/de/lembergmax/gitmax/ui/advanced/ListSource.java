package de.lembergmax.gitmax.ui.advanced;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import de.lembergmax.gitmax.domain.GitFailureException;

import java.util.List;
import java.util.Objects;

/**
 * Was eine einfache Liste anzeigt und kann: Zeilen laden, Aktionen je Zeile anbieten und ausführen, auf
 * Wunsch einen Eintrag anlegen. Jede Methode außer den Beschreibungen blockiert und läuft im Hintergrund.
 */
public interface ListSource {

    /** Ein Eingabefeld im Dialog; ein geheimes Feld zeigt seinen Inhalt nicht (Passphrase). */
    record Field(
            @StringRes int hint,
            @NonNull String initial,
            boolean multiLine,
            boolean required,
            boolean secret
    ) {

        public Field {
            Objects.requireNonNull(initial, "initial");
        }

        /** Ein gewöhnliches Feld. */
        public Field(
                @StringRes final int hint,
                @NonNull final String initial,
                final boolean multiLine,
                final boolean required
        ) {
            this(hint, initial, multiLine, required, false);
        }
    }

    /** Ein Schalter im Dialog. */
    record Toggle(
            @StringRes int label,
            boolean checked
    ) {
    }

    /** Ein Eingabedialog mit Feldern und Schaltern. */
    record Prompt(
            @StringRes int title,
            @StringRes int confirm,
            @NonNull List<Field> fields,
            @NonNull List<Toggle> toggles
    ) {

        public Prompt {
            fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
            toggles = List.copyOf(Objects.requireNonNull(toggles, "toggles"));
        }
    }

    /** Eine Rückfrage vor einer folgenreichen Aktion. */
    record Confirm(
            @StringRes int title,
            @NonNull String body,
            @StringRes int confirm
    ) {

        public Confirm {
            Objects.requireNonNull(body, "body");
        }
    }

    /** Eine Aktion im Menü einer Zeile; mit Eingabedialog und/oder Rückfrage. */
    record RowAction(
            @NonNull String id,
            @StringRes int label,
            @Nullable Prompt prompt,
            @Nullable Confirm confirm
    ) {

        public RowAction {
            Objects.requireNonNull(id, "id");
        }
    }

    /** Eingaben aus einem Dialog: Text je Feld und Zustand je Schalter, in der Reihenfolge der Beschreibung. */
    record Values(
            @NonNull List<String> fields,
            @NonNull List<Boolean> toggles
    ) {

        public static final Values NONE = new Values(List.of(), List.of());

        public Values {
            fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
            toggles = List.copyOf(Objects.requireNonNull(toggles, "toggles"));
        }

        @NonNull
        public String field(
                final int index
        ) {
            return fields.get(index);
        }

        public boolean toggle(
                final int index
        ) {
            return toggles.get(index);
        }
    }

    @StringRes
    int title();

    @StringRes
    int emptyTitle();

    @StringRes
    int emptyBody();

    @NonNull
    List<SimpleRow> load() throws GitFailureException;

    @NonNull
    List<RowAction> actions(
            @NonNull SimpleRow row
    );

    /** Dialog zum Anlegen eines Eintrags; {@code null}, wenn die Liste das nicht kennt. */
    @Nullable
    Prompt createPrompt();

    void create(
            @NonNull Values values
    ) throws GitFailureException;

    /**
     * Führt eine Aktion aus.
     *
     * @return Text-Ressource einer Erfolgsmeldung oder 0
     */
    @StringRes
    int perform(
            @NonNull SimpleRow row,
            @NonNull String actionId,
            @NonNull Values values
    ) throws GitFailureException;
}
