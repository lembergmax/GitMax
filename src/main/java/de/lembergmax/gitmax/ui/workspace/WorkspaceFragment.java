package de.lembergmax.gitmax.ui.workspace;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.FragmentWorkspaceBinding;
import de.lembergmax.gitmax.storage.StoragePermission;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;
import de.lembergmax.gitmax.ui.picker.FolderPickerFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Arbeitsordner verwalten: hinzufügen, entfernen und den Standard-Zielordner für neue Klone wählen.
 */
public final class WorkspaceFragment extends Fragment {

    private FragmentWorkspaceBinding binding;
    private WorkspaceViewModel viewModel;
    private RootAdapter adapter;

    public WorkspaceFragment() {
        super(R.layout.fragment_workspace);
    }

    @Override
    public void onCreate(
            @Nullable final Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        setEnterTransition(Motion.sharedAxis(requireContext(), MaterialSharedAxis.X, true));
        setReturnTransition(Motion.sharedAxis(requireContext(), MaterialSharedAxis.X, false));
        setExitTransition(Motion.sharedAxis(requireContext(), MaterialSharedAxis.X, true));
        setReenterTransition(Motion.sharedAxis(requireContext(), MaterialSharedAxis.X, false));
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentWorkspaceBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(WorkspaceViewModel.class);

        Toolbars.setupBack(this, binding.toolbar);
        adapter = new RootAdapter(this::showMenu);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.fabAdd.setOnClickListener(button ->
                NavHostFragment.findNavController(this).navigate(R.id.folderPickerFragment));
        binding.permissionAction.setOnClickListener(button -> openPermissionSettings());

        getParentFragmentManager().setFragmentResultListener(
                FolderPickerFragment.RESULT_KEY,
                getViewLifecycleOwner(),
                (key, result) -> {
                    final String path = result.getString(FolderPickerFragment.RESULT_PATH);
                    if (path != null) {
                        viewModel.add(new File(path));
                    }
                }
        );

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.messages().observe(getViewLifecycleOwner(), this::showMessage);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.reload();
        final int visibility = StoragePermission.isGranted() ? View.GONE : View.VISIBLE;
        binding.permissionBanner.setVisibility(visibility);
        binding.permissionAction.setVisibility(visibility);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        adapter = null;
        super.onDestroyView();
    }

    private void render(
            @NonNull final WorkspaceViewModel.State state
    ) {
        final List<RootAdapter.Item> items = new ArrayList<>();
        for (final File root : state.roots()) {
            items.add(new RootAdapter.Item(root, root.equals(state.defaultTarget())));
        }
        adapter.submitList(items);
        binding.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showMenu(
            @NonNull final View anchor,
            @NonNull final RootAdapter.Item item
    ) {
        final PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.inflate(R.menu.menu_root_item);
        menu.getMenu().findItem(R.id.action_make_default).setVisible(!item.isDefault());
        menu.setOnMenuItemClickListener(entry -> {
            if (entry.getItemId() == R.id.action_make_default) {
                viewModel.makeDefault(item.folder());
                return true;
            }
            if (entry.getItemId() == R.id.action_remove) {
                remove(item.folder(), item.isDefault());
                return true;
            }
            return false;
        });
        menu.show();
    }

    /** Entfernt sofort und bietet Rückgängig an, statt vorher zu fragen. */
    private void remove(
            @NonNull final File folder,
            final boolean wasDefault
    ) {
        viewModel.remove(folder);
        Snackbar.make(binding.getRoot(), R.string.workspace_removed, Snackbar.LENGTH_LONG)
                .setAction(R.string.workspace_undo, undo -> viewModel.restore(folder, wasDefault))
                .show();
    }

    private void openPermissionSettings() {
        try {
            startActivity(StoragePermission.settingsIntent(requireContext()));
        } catch (final ActivityNotFoundException noSettings) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    private void showMessage(
            @Nullable final Event<String> event
    ) {
        final String message = event == null ? null : event.consume();
        if (message != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }
}
