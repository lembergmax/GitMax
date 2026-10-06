package de.lembergmax.gitmax.ops;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.git.GitProgress;

/** Texte für Vorgänge in der Sprache der App: Art, Abschnitt, Ergebnis und Fehler. */
public final class OperationTexts {

    private static final int PERCENT = 100;

    private OperationTexts() {
    }

    @NonNull
    public static String kind(
            @NonNull final Context context,
            @NonNull final OperationKind kind
    ) {
        switch (kind) {
            case CLONE:
                return context.getString(R.string.op_kind_clone);
            case UPDATE:
                return context.getString(R.string.op_kind_update);
            case FETCH:
                return context.getString(R.string.op_kind_fetch);
            case CREATE:
                return context.getString(R.string.op_kind_create);
            case PUSH:
            default:
                return context.getString(R.string.op_kind_push);
        }
    }

    /** Der Abschnitt, bei bekanntem Anteil mit Prozentangabe. */
    @NonNull
    public static String progress(
            @NonNull final Context context,
            @NonNull final OperationEntry.Progress progress
    ) {
        final String phase = context.getString(phaseText(progress.phase()));
        if (progress.fraction() < 0f) {
            return phase;
        }
        final int percent = Math.round(Math.min(1f, progress.fraction()) * PERCENT);
        return context.getString(R.string.op_phase_with_percent, phase, percent);
    }

    @NonNull
    public static String outcome(
            @NonNull final Context context,
            @NonNull final OperationOutcome outcome
    ) {
        return context.getString(outcomeText(outcome));
    }

    /** Handlungsorientierte Beschreibung eines Fehlers; die technische Meldung steht nur im Protokoll. */
    @NonNull
    public static String failure(
            @NonNull final Context context,
            @NonNull final OperationEntry.Failure failure
    ) {
        if (failure.kind() == GitFailureKind.CANCELLED && OperationHistory.INTERRUPTED_MESSAGE.equals(failure.message())) {
            return context.getString(R.string.op_failure_interrupted);
        }
        if (failure.kind() == GitFailureKind.CONFLICT && !failure.paths().isEmpty()) {
            return context.getResources().getQuantityString(R.plurals.op_failure_conflict_files, failure.paths().size(),
                    failure.paths().size());
        }
        return context.getString(failureText(failure.kind()));
    }

    /** Eine Zeile für Listen: wartet, Fortschritt, Ergebnis oder Fehler. */
    @NonNull
    public static String status(
            @NonNull final Context context,
            @NonNull final OperationEntry entry
    ) {
        switch (entry.state()) {
            case QUEUED:
                return context.getString(R.string.op_queued);
            case RUNNING:
                return entry.progress() == null
                        ? context.getString(R.string.op_phase_connecting)
                        : progress(context, entry.progress());
            case SUCCEEDED:
                return entry.outcome() == null ? "" : outcome(context, entry.outcome());
            case FAILED:
            case CANCELLED:
            default:
                return entry.failure() == null
                        ? context.getString(R.string.op_failure_unknown)
                        : failure(context, entry.failure());
        }
    }

    @StringRes
    private static int phaseText(
            final GitProgress.Phase phase
    ) {
        switch (phase) {
            case CONNECTING:
                return R.string.op_phase_connecting;
            case FETCHING:
                return R.string.op_phase_fetching;
            case RECEIVING:
                return R.string.op_phase_receiving;
            case RESOLVING:
                return R.string.op_phase_resolving;
            case CHECKOUT:
                return R.string.op_phase_checkout;
            case SUBMODULES:
                return R.string.op_phase_submodules;
            case MERGING:
                return R.string.op_phase_merging;
            case PUSHING:
                return R.string.op_phase_pushing;
            case WORKING:
            default:
                return R.string.op_phase_working;
        }
    }

    @StringRes
    private static int outcomeText(
            final OperationOutcome outcome
    ) {
        switch (outcome) {
            case CLONED:
                return R.string.op_outcome_cloned;
            case UP_TO_DATE:
                return R.string.op_outcome_up_to_date;
            case FAST_FORWARDED:
                return R.string.op_outcome_fast_forwarded;
            case MERGED:
                return R.string.op_outcome_merged;
            case REBASED:
                return R.string.op_outcome_rebased;
            case SKIPPED_NO_UPSTREAM:
                return R.string.op_outcome_skipped_no_upstream;
            case SKIPPED_DETACHED:
                return R.string.op_outcome_skipped_detached;
            case FETCHED:
                return R.string.op_outcome_fetched;
            case CREATED:
                return R.string.op_outcome_created;
            case PUSHED:
            default:
                return R.string.op_outcome_pushed;
        }
    }

    @StringRes
    private static int failureText(
            final GitFailureKind kind
    ) {
        switch (kind) {
            case AUTH:
                return R.string.op_failure_auth;
            case NOT_FOUND:
                return R.string.op_failure_not_found;
            case NETWORK:
                return R.string.op_failure_network;
            case NOT_FAST_FORWARD:
                return R.string.op_failure_not_fast_forward;
            case REJECTED:
                return R.string.op_failure_rejected;
            case CONFLICT:
                return R.string.op_failure_conflict;
            case DIRTY_TREE:
                return R.string.op_failure_dirty_tree;
            case NO_UPSTREAM:
                return R.string.op_failure_no_upstream;
            case NOT_A_REPO:
                return R.string.op_failure_not_a_repo;
            case NOTHING_TO_COMMIT:
                return R.string.op_failure_nothing_to_commit;
            case ALREADY_EXISTS:
                return R.string.op_failure_already_exists;
            case DISK_FULL:
                return R.string.op_failure_disk_full;
            case INVALID_PATH:
                return R.string.op_failure_invalid_path;
            case INVALID_NAME:
                return R.string.op_failure_invalid_name;
            case NOT_MERGED:
                return R.string.op_failure_not_merged;
            case CURRENT_BRANCH:
                return R.string.op_failure_current_branch;
            case HOST_KEY_UNKNOWN:
                return R.string.op_failure_host_key_unknown;
            case HOST_KEY_CHANGED:
                return R.string.op_failure_host_key_changed;
            case SSH_NO_KEY:
                return R.string.op_failure_ssh_no_key;
            case SSH_REJECTED:
                return R.string.op_failure_ssh_rejected;
            case INSECURE_TRANSPORT:
                return R.string.op_failure_insecure;
            case METERED_NETWORK:
                return R.string.op_failure_metered;
            case CANCELLED:
                return R.string.op_failure_cancelled;
            case UNKNOWN:
            default:
                return R.string.op_failure_unknown;
        }
    }
}
