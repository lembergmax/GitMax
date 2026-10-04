package de.lembergmax.gitmax.ui.repo;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.SheetCommitBinding;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.RepoStatus;
import de.lembergmax.gitmax.domain.model.WorkingTree;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.IdentityDialog;

import java.util.Optional;

/**
 * Commit-Sheet: Nachricht, Identität und Optionen. Committet die vorgemerkten Dateien, auf Wunsch alle
 * Änderungen, und pusht danach, wenn gewünscht.
 */
public final class CommitSheetFragment extends BottomSheetDialogFragment {

    /** Schlüssel des Fragment-Ergebnisses bei Erfolg. */
    public static final String RESULT_KEY = "commit_sheet_result";

    /** Gekürzte Kennung des neuen Commits im Ergebnis. */
    public static final String RESULT_ID = "id";

    private static final String TAG = "CommitSheet";

    private SheetCommitBinding binding;
    private RepoDetailViewModel viewModel;
    private CommitIdentity identity;

    /** Zeigt das Sheet; das Ergebnis geht an den Child-Fragment-Manager des Aufrufers. */
    public static void show(
            @NonNull final FragmentManager manager
    ) {
        new CommitSheetFragment().show(manager, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(
            @Nullable final Bundle savedInstanceState
    ) {
        final BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull final LayoutInflater inflater,
            @Nullable final ViewGroup container,
            @Nullable final Bundle savedInstanceState
    ) {
        binding = SheetCommitBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(RepoDetailViewModel.class);
        // Ein Ergebnis, das niemand mehr abgeholt hat, gehört nicht in dieses Sheet.
        final Event<RepoDetailViewModel.CommitResult> stale = viewModel.commitResults().getValue();
        if (stale != null) {
            stale.consume();
        }

        final Optional<CommitIdentity> resolved = viewModel.identity();
        identity = resolved.orElse(null);
        renderIdentity();
        renderTree(viewModel.state().getValue());

        binding.identityChange.setOnClickListener(button -> askForIdentity(null));
        binding.confirm.setOnClickListener(button -> confirm(false));
        binding.confirmPush.setOnClickListener(button -> confirm(true));
        binding.optionAmend.setOnCheckedChangeListener((button, checked) -> renderAmendHint());
        viewModel.commitResults().observe(getViewLifecycleOwner(), this::onResult);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void renderTree(
            @Nullable final RepoDetailViewModel.UiState state
    ) {
        binding.lfsNote.setVisibility(state != null && state.usesLfs() ? View.VISIBLE : View.GONE);
        final WorkingTree tree = state == null ? null : state.tree();
        if (tree == null) {
            return;
        }
        final int staged = tree.staged().size();
        final boolean offerStageAll = staged == 0 && (!tree.unstaged().isEmpty() || !tree.untracked().isEmpty());
        binding.optionStageAll.setVisibility(offerStageAll ? View.VISIBLE : View.GONE);
        binding.optionStageAll.setChecked(offerStageAll);
        binding.stageAllHint.setVisibility(offerStageAll ? View.VISIBLE : View.GONE);
        binding.summary.setText(staged > 0 ? getResources().getQuantityString(R.plurals.commit_staged_count, staged, staged) : "");
        binding.summary.setVisibility(staged > 0 ? View.VISIBLE : View.GONE);
    }

    /** Ein schon gepushter letzter Commit lässt sich nach dem Ändern nur mit erzwungenem Push angleichen. */
    private void renderAmendHint() {
        final RepoDetailViewModel.UiState state = viewModel.state().getValue();
        final RepoStatus status = state == null ? null : state.status();
        final boolean pushed = status != null && status.hasUpstream() && status.ahead() == 0;
        binding.amendHint.setVisibility(binding.optionAmend.isChecked() && pushed ? View.VISIBLE : View.GONE);
    }

    private void renderIdentity() {
        if (identity == null) {
            binding.identity.setText(R.string.commit_identity_missing);
            binding.identityChange.setText(R.string.commit_identity_set);
        } else {
            binding.identity.setText(getString(R.string.commit_identity, identity.name(), identity.email()));
            binding.identityChange.setText(R.string.commit_identity_change);
        }
    }

    private void confirm(
            final boolean thenPush
    ) {
        final String message = String.valueOf(binding.message.getText()).strip();
        if (message.isEmpty()) {
            binding.messageLayout.setError(getString(R.string.commit_message_error));
            return;
        }
        binding.messageLayout.setError(null);
        final RepoDetailViewModel.UiState state = viewModel.state().getValue();
        final WorkingTree tree = state == null ? null : state.tree();
        if (tree != null && !tree.conflicts().isEmpty()) {
            binding.messageLayout.setError(getString(R.string.commit_conflicts));
            return;
        }
        final boolean stageAll = binding.optionStageAll.getVisibility() == View.VISIBLE && binding.optionStageAll.isChecked();
        final boolean amend = binding.optionAmend.isChecked();
        if (tree != null && tree.staged().isEmpty() && !stageAll && !amend) {
            binding.messageLayout.setError(getString(R.string.commit_nothing));
            return;
        }
        if (identity == null) {
            askForIdentity(() -> confirm(thenPush));
            return;
        }
        setBusy(true);
        viewModel.commit(message, identity, amend, stageAll, thenPush);
    }

    private void onResult(
            @Nullable final Event<RepoDetailViewModel.CommitResult> event
    ) {
        final RepoDetailViewModel.CommitResult result = event == null ? null : event.consume();
        if (result == null) {
            return;
        }
        if (result.shortId() != null) {
            final Bundle bundle = new Bundle();
            bundle.putString(RESULT_ID, result.shortId());
            getParentFragmentManager().setFragmentResult(RESULT_KEY, bundle);
            dismiss();
        } else {
            setBusy(false);
            binding.messageLayout.setError(result.error());
        }
    }

    private void setBusy(
            final boolean busy
    ) {
        binding.confirm.setEnabled(!busy);
        binding.confirmPush.setEnabled(!busy);
        binding.message.setEnabled(!busy);
    }

    /** Fragt Name und E-Mail ab, merkt sie für dieses Repo und führt dann {@code afterwards} aus. */
    private void askForIdentity(
            @Nullable final Runnable afterwards
    ) {
        IdentityDialog.show(requireContext(), getLayoutInflater(), identity, chosen -> {
            identity = chosen;
            viewModel.chooseIdentity(identity);
            renderIdentity();
            if (afterwards != null) {
                afterwards.run();
            }
        });
    }
}
