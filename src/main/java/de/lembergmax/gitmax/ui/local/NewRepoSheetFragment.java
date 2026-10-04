package de.lembergmax.gitmax.ui.local;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.SheetNewRepoBinding;
import de.lembergmax.gitmax.domain.RepoNames;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.ui.common.NotificationGate;

import java.io.File;
import java.util.List;

/**
 * Sheet zum Anlegen eines neuen Repos: Konto, Name, Beschreibung, Sichtbarkeit, README und Zielordner. Das eigentliche
 * Anlegen läuft als Vorgang in der Warteschlange; das Ergebnis geht als Fragment-Ergebnis an den Aufrufer.
 */
public final class NewRepoSheetFragment extends BottomSheetDialogFragment {

    /** Schlüssel des Fragment-Ergebnisses. */
    public static final String RESULT_KEY = "new_repo_sheet_result";

    private static final String TAG = "NewRepoSheet";

    private SheetNewRepoBinding binding;
    private NewRepoSheetViewModel viewModel;
    private NotificationGate notificationGate;

    /** Zeigt das Sheet; der Aufrufer bekommt das Ergebnis über seinen Child-Fragment-Manager. */
    public static void show(
            @NonNull final FragmentManager manager
    ) {
        new NewRepoSheetFragment().show(manager, TAG);
    }

    @Override
    public void onCreate(
            @Nullable final Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        notificationGate = new NotificationGate(this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(
            @Nullable final Bundle savedInstanceState
    ) {
        final BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        // Die Tastatur darf das Sheet nicht verdecken: es klappt vollständig auf.
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull final LayoutInflater inflater,
            @Nullable final ViewGroup container,
            @Nullable final Bundle savedInstanceState
    ) {
        binding = SheetNewRepoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(NewRepoSheetViewModel.class);
        binding.accountChange.setOnClickListener(button -> chooseAccount());
        binding.targetChange.setOnClickListener(button -> chooseTarget());
        binding.confirm.setOnClickListener(button -> notificationGate.run(this::submit));
        binding.name.addTextChangedListener(new SimpleTextWatcher(this::showFolder));
        viewModel.state().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void render(
            @NonNull final NewRepoSheetViewModel.State state
    ) {
        binding.account.setText(state.account() == null
                ? getString(R.string.new_repo_no_account)
                : getString(R.string.new_repo_account, state.account().label()));
        binding.accountChange.setVisibility(state.accounts().size() > 1 ? View.VISIBLE : View.GONE);
        binding.targetChange.setVisibility(state.roots().size() > 1 ? View.VISIBLE : View.GONE);
        showFolder();
        binding.confirm.setEnabled(state.account() != null && state.target() != null);
        binding.problem.setVisibility(state.account() == null || state.target() == null ? View.VISIBLE : View.GONE);
        binding.problem.setText(state.account() == null ? R.string.new_repo_no_account_hint : R.string.clone_target_missing);
    }

    /** Zeigt den Ordner, in dem das Repo entstünde, samt Namen. */
    private void showFolder() {
        if (binding == null || viewModel == null) {
            return;
        }
        final String name = String.valueOf(binding.name.getText()).strip();
        final NewRepoSheetViewModel.State state = viewModel.state().getValue();
        if (state == null || state.target() == null) {
            binding.targetPath.setText(R.string.clone_target_missing);
            return;
        }
        binding.targetPath.setText(new File(state.target(), name.isEmpty() ? "…" : name).getAbsolutePath());
    }

    private void chooseAccount() {
        final NewRepoSheetViewModel.State state = viewModel.state().getValue();
        if (state == null) {
            return;
        }
        final List<Account> accounts = state.accounts();
        final String[] labels = new String[accounts.size()];
        int checked = -1;
        for (int index = 0; index < accounts.size(); index += 1) {
            labels[index] = accounts.get(index).label();
            if (accounts.get(index).equals(state.account())) {
                checked = index;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.new_repo_account_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    viewModel.setAccount(accounts.get(which));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.activity_cancel, null)
                .show();
    }

    private void chooseTarget() {
        final NewRepoSheetViewModel.State state = viewModel.state().getValue();
        if (state == null) {
            return;
        }
        final List<File> roots = state.roots();
        final String[] labels = new String[roots.size()];
        int checked = -1;
        for (int index = 0; index < roots.size(); index += 1) {
            labels[index] = roots.get(index).getAbsolutePath();
            if (roots.get(index).equals(state.target())) {
                checked = index;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clone_target_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    viewModel.setTarget(roots.get(which));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.activity_cancel, null)
                .show();
    }

    private void submit() {
        if (binding == null) {
            return;
        }
        binding.nameLayout.setError(null);
        binding.problem.setVisibility(View.GONE);
        final NewRepoSheetViewModel.Result result = viewModel.submit(
                String.valueOf(binding.name.getText()),
                String.valueOf(binding.description.getText()),
                binding.optionPrivate.isChecked(),
                binding.optionReadme.isChecked());
        if (result.started()) {
            getParentFragmentManager().setFragmentResult(RESULT_KEY, new Bundle());
            dismiss();
            return;
        }
        if (result.nameProblem() != null) {
            binding.nameLayout.setError(getString(textFor(result.nameProblem())));
        } else if (result.refusal() == NewRepoSheetViewModel.Refusal.FOLDER_EXISTS) {
            binding.nameLayout.setError(getString(R.string.new_repo_error_folder_exists));
        } else {
            binding.problem.setVisibility(View.VISIBLE);
            binding.problem.setText(result.refusal() == NewRepoSheetViewModel.Refusal.NO_ACCOUNT
                    ? R.string.new_repo_no_account_hint : R.string.clone_target_missing);
        }
    }

    private static int textFor(
            @NonNull final RepoNames.Problem problem
    ) {
        switch (problem) {
            case EMPTY:
                return R.string.new_repo_error_empty;
            case TOO_LONG:
                return R.string.new_repo_error_too_long;
            case RESERVED:
                return R.string.new_repo_error_reserved;
            case INVALID_CHARACTERS:
            default:
                return R.string.new_repo_error_characters;
        }
    }

    /** Ruft {@code onChanged} nach jeder Eingabe auf. */
    private static final class SimpleTextWatcher implements android.text.TextWatcher {

        private final Runnable onChanged;

        private SimpleTextWatcher(
                final Runnable onChanged
        ) {
            this.onChanged = onChanged;
        }

        @Override
        public void beforeTextChanged(
                final CharSequence text,
                final int start,
                final int count,
                final int after
        ) {
            // nichts zu tun
        }

        @Override
        public void onTextChanged(
                final CharSequence text,
                final int start,
                final int before,
                final int count
        ) {
            onChanged.run();
        }

        @Override
        public void afterTextChanged(
                final android.text.Editable text
        ) {
            // nichts zu tun
        }
    }
}
