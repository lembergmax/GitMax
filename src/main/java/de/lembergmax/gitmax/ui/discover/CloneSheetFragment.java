package de.lembergmax.gitmax.ui.discover;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.ItemClonePlanBinding;
import de.lembergmax.gitmax.databinding.SheetCloneBinding;
import de.lembergmax.gitmax.domain.ClonePlanner;
import de.lembergmax.gitmax.ui.common.NotificationGate;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Bestätigungs-Sheet vor dem Klonen: zeigt den Zielordner und den Ordner jedes Repos, bietet die
 * Optionen und startet die Vorgänge. Das Ergebnis geht als Fragment-Ergebnis an den Aufrufer.
 */
public final class CloneSheetFragment extends BottomSheetDialogFragment {

    /** Schlüssel des Fragment-Ergebnisses. */
    public static final String RESULT_KEY = "clone_sheet_result";

    /** Zahl der gestarteten Vorgänge im Ergebnis. */
    public static final String RESULT_STARTED = "started";

    private static final String TAG = "CloneSheet";
    private static final String ARG_URLS = "urls";

    private SheetCloneBinding binding;
    private CloneSheetViewModel viewModel;
    private NotificationGate notificationGate;

    /** Zeigt das Sheet für die Adressen; der Aufrufer bekommt das Ergebnis über seinen Child-Fragment-Manager. */
    public static void show(
            @NonNull final FragmentManager manager,
            @NonNull final List<String> urls
    ) {
        final CloneSheetFragment sheet = new CloneSheetFragment();
        final Bundle arguments = new Bundle();
        arguments.putStringArrayList(ARG_URLS, new ArrayList<>(urls));
        sheet.setArguments(arguments);
        sheet.show(manager, TAG);
    }

    @Override
    public void onCreate(
            @Nullable final Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        notificationGate = new NotificationGate(this);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull final LayoutInflater inflater,
            @Nullable final ViewGroup container,
            @Nullable final Bundle savedInstanceState
    ) {
        binding = SheetCloneBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CloneSheetViewModel.class);
        final ArrayList<String> urls = requireArguments().getStringArrayList(ARG_URLS);
        viewModel.init(urls == null ? List.of() : urls);

        binding.targetChange.setOnClickListener(button -> chooseTarget());
        binding.confirm.setOnClickListener(button -> confirm());
        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        binding.optionSsh.setOnCheckedChangeListener((button, checked) -> {
            final CloneSheetViewModel.State current = viewModel.state().getValue();
            if (current != null) {
                render(current);
            }
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void render(
            @NonNull final CloneSheetViewModel.State state
    ) {
        binding.title.setText(state.plans().size() == 1
                ? getString(R.string.clone_title_one)
                : getString(R.string.clone_title_many, state.plans().size()));
        binding.targetPath.setText(state.target() == null
                ? getString(R.string.clone_target_missing)
                : state.target().getAbsolutePath());
        binding.targetChange.setVisibility(state.roots().size() > 1 ? View.VISIBLE : View.GONE);

        binding.planContainer.removeAllViews();
        final LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (final ClonePlanner.Plan plan : state.plans()) {
            final ItemClonePlanBinding row = ItemClonePlanBinding.inflate(inflater, binding.planContainer, true);
            row.name.setText(plan.url().displayName());
            row.folder.setText(getString(plan.alreadyCloned() ? R.string.clone_row_exists : R.string.clone_row_new,
                    plan.folderName()));
        }

        binding.optionUpdateExisting.setVisibility(state.existingCount() > 0 ? View.VISIBLE : View.GONE);
        final boolean missingKey = binding.optionSsh.isChecked() && !viewModel.hasSshKey();
        binding.optionSshHint.setText(missingKey ? R.string.clone_option_ssh_missing : R.string.clone_option_ssh_hint);
        binding.optionSshHint.setTextColor(MaterialColors.getColor(binding.optionSshHint,
                missingKey ? androidx.appcompat.R.attr.colorError : com.google.android.material.R.attr.colorOnSurfaceVariant));
        final boolean hasWork = state.target() != null && !state.plans().isEmpty() && !missingKey;
        binding.confirm.setEnabled(hasWork);
        binding.confirm.setText(state.newCount() == 0 && state.existingCount() > 0
                ? R.string.clone_confirm_update
                : R.string.clone_confirm);
    }

    private void chooseTarget() {
        final CloneSheetViewModel.State state = viewModel.state().getValue();
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

    private void confirm() {
        notificationGate.run(this::begin);
    }

    private void begin() {
        if (binding == null) {
            return;
        }
        final int started = viewModel.start(
                binding.optionShallow.isChecked(),
                binding.optionSubmodules.isChecked(),
                binding.optionUpdateExisting.getVisibility() == View.VISIBLE && binding.optionUpdateExisting.isChecked(),
                binding.optionSsh.isChecked());
        final Bundle result = new Bundle();
        result.putInt(RESULT_STARTED, started);
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
        dismiss();
    }
}
