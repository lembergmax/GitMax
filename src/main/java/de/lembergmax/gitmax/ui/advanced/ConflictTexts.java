package de.lembergmax.gitmax.ui.advanced;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.domain.model.ConflictKind;
import de.lembergmax.gitmax.domain.model.RunningOperation;

/**
 * Texte der Konfliktlösung. Beim Aufsetzen sind „meine“ und „andere“ Seite gegenüber einem Merge
 * vertauscht: Git nennt dort den Ziel-Branch „ours“ und den aufgesetzten Commit „theirs“. Die Texte sagen
 * deshalb, was gemeint ist, nicht was Git intern so heißt.
 */
final class ConflictTexts {

    private ConflictTexts() {
    }

    @NonNull
    static String running(
            @NonNull final Context context,
            @NonNull final RunningOperation operation
    ) {
        switch (operation) {
            case REBASE:
                return context.getString(R.string.conflicts_running_rebase);
            case CHERRY_PICK:
                return context.getString(R.string.conflicts_running_cherry_pick);
            case REVERT:
                return context.getString(R.string.conflicts_running_revert);
            case MERGE:
            case NONE:
            default:
                return context.getString(R.string.conflicts_running_merge);
        }
    }

    /** Beschriftung der Auswahl „eigene Seite von Git“ ({@code OURS}). */
    @StringRes
    static int ours(
            @NonNull final RunningOperation operation
    ) {
        return operation == RunningOperation.REBASE ? R.string.conflicts_take_target : R.string.conflicts_take_mine;
    }

    /** Beschriftung der Auswahl „andere Seite von Git“ ({@code THEIRS}). */
    @StringRes
    static int theirs(
            @NonNull final RunningOperation operation
    ) {
        return operation == RunningOperation.REBASE ? R.string.conflicts_take_mine_rebase : R.string.conflicts_take_theirs;
    }

    @StringRes
    static int kind(
            @NonNull final ConflictKind kind,
            @NonNull final RunningOperation operation
    ) {
        final boolean rebase = operation == RunningOperation.REBASE;
        switch (kind) {
            case BOTH_ADDED:
                return R.string.conflicts_kind_both_added;
            case BOTH_DELETED:
                return R.string.conflicts_kind_both_deleted;
            case DELETED_BY_US:
                return rebase ? R.string.conflicts_kind_rebase_deleted_by_us : R.string.conflicts_kind_deleted_by_us;
            case DELETED_BY_THEM:
                return rebase ? R.string.conflicts_kind_rebase_deleted_by_them : R.string.conflicts_kind_deleted_by_them;
            case ADDED_BY_US:
                return rebase ? R.string.conflicts_kind_rebase_added_by_us : R.string.conflicts_kind_added_by_us;
            case ADDED_BY_THEM:
                return rebase ? R.string.conflicts_kind_rebase_added_by_them : R.string.conflicts_kind_added_by_them;
            case BOTH_MODIFIED:
            default:
                return R.string.conflicts_kind_both_modified;
        }
    }
}
