package de.lembergmax.gitmax.ui.picker;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.DialogNewFolderBinding;
import de.lembergmax.gitmax.databinding.FragmentFolderPickerBinding;
import de.lembergmax.gitmax.domain.FolderName;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.InsetsPadding;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;

import java.io.File;

/**
 * Eigene Ordnerauswahl: durch Speicher und Ordner blättern, Ordner anlegen und einen wählen. Das
 * Ergebnis kommt als Fragment-Ergebnis {@link #RESULT_KEY} mit dem Pfad unter {@link #RESULT_PATH}.
 */
public final class FolderPickerFragment extends Fragment {

    public static final String RESULT_KEY = "folder_picked";
    public static final String RESULT_PATH = "path";
    public static final String ARG_START_PATH = "startPath";

    private FragmentFolderPickerBinding binding;
    private FolderPickerViewModel viewModel;
    private FolderAdapter adapter;
    private OnBackPressedCallback backCallback;

    public FolderPickerFragment() {
        super(R.layout.fragment_folder_picker);
    }

    @Override
    public void onCreate(
            @Nullable final Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        setEnterTransition(Motion.sharedAxis(requireContext(), MaterialSharedAxis.X, true));
        setReturnTransition(Motion.sharedAxis(requireContext(), MaterialSharedAxis.X, false));
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentFolderPickerBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(FolderPickerViewModel.class);

        Toolbars.setupBack(this, binding.toolbar);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_new_folder) {
                showNewFolderDialog();
                return true;
            }
            return false;
        });
        InsetsPadding.apply(binding.select, false, true, false);

        adapter = new FolderAdapter(this::onEntryClick);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.select.setOnClickListener(button -> selectCurrent());

        // Zurück geht erst einen Ordner nach oben und verlässt die Auswahl an der Speicher-Liste.
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                viewModel.up();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);

        viewModel.listing().observe(getViewLifecycleOwner(), this::showListing);
        viewModel.messages().observe(getViewLifecycleOwner(), this::showMessage);

        final Bundle arguments = getArguments();
        viewModel.start(arguments == null ? null : arguments.getString(ARG_START_PATH));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        adapter = null;
        backCallback = null;
        super.onDestroyView();
    }

    private void showListing(
            @Nullable final FolderPickerViewModel.Listing listing
    ) {
        if (listing == null) {
            return;
        }
        adapter.submitList(listing.entries());
        final File directory = listing.directory();
        binding.toolbar.setSubtitle(directory == null ? getString(R.string.picker_volumes) : directory.getAbsolutePath());
        binding.select.setEnabled(directory != null);
        binding.toolbar.getMenu().findItem(R.id.action_new_folder).setVisible(directory != null);
        backCallback.setEnabled(directory != null);

        final boolean noFolders = directory != null && listing.entries().stream()
                .noneMatch(entry -> entry.type() == FolderPickerViewModel.Type.FOLDER);
        binding.empty.setVisibility(noFolders ? View.VISIBLE : View.GONE);
    }

    private void onEntryClick(
            @NonNull final FolderPickerViewModel.Entry entry
    ) {
        switch (entry.type()) {
            case UP:
                viewModel.up();
                break;
            case VOLUME:
            case FOLDER:
                if (entry.file() != null) {
                    viewModel.open(entry.file());
                }
                break;
            default:
                break;
        }
    }

    private void selectCurrent() {
        final FolderPickerViewModel.Listing listing = viewModel.listing().getValue();
        if (listing == null || listing.directory() == null) {
            return;
        }
        if (!listing.directory().canWrite()) {
            Snackbar.make(binding.getRoot(), R.string.picker_error_not_writable, Snackbar.LENGTH_LONG).show();
            return;
        }
        final Bundle result = new Bundle();
        result.putString(RESULT_PATH, listing.directory().getAbsolutePath());
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
        NavHostFragment.findNavController(this).navigateUp();
    }

    private void showMessage(
            @Nullable final Event<String> event
    ) {
        final String message = event == null ? null : event.consume();
        if (message != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }

    private void showNewFolderDialog() {
        final DialogNewFolderBinding dialogBinding = DialogNewFolderBinding.inflate(LayoutInflater.from(requireContext()));
        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.picker_new_folder_title)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.account_detail_cancel, null)
                .setPositiveButton(R.string.picker_new_folder_create, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            final String name = String.valueOf(dialogBinding.nameInput.getText());
            if (!FolderName.isValid(name)) {
                dialogBinding.nameLayout.setError(getString(R.string.picker_error_name));
                return;
            }
            dialog.dismiss();
            viewModel.createFolder(name);
        });
    }
}
