package de.lembergmax.gitmax.ui.files;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.DialogNewFolderBinding;
import de.lembergmax.gitmax.databinding.FragmentFilesBinding;
import de.lembergmax.gitmax.storage.RepoFiles;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.ExternalFiles;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;
import de.lembergmax.gitmax.ui.viewer.FileViewerFragment;

import java.io.File;
import java.util.function.Consumer;

/** Dateibaum eines Repos: ein Ordner nach dem anderen, mit Anlegen, Umbenennen, Löschen und Teilen. */
public final class FilesFragment extends Fragment {

    /** Pfad des Repos. */
    public static final String ARG_DIRECTORY = "directory";

    /** Ordner im Repo, relativ und mit {@code /}; leer für die Wurzel. */
    public static final String ARG_PATH = "path";

    private FragmentFilesBinding binding;
    private FilesViewModel viewModel;
    private FileAdapter adapter;

    public FilesFragment() {
        super(R.layout.fragment_files);
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
        binding = FragmentFilesBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(FilesViewModel.class);
        final String directory = requireArguments().getString(ARG_DIRECTORY);
        if (directory == null) {
            throw new IllegalStateException("No repo path passed");
        }
        final String path = requireArguments().getString(ARG_PATH, "");
        viewModel.init(directory, path);

        Toolbars.setupBack(this, binding.toolbar);
        binding.toolbar.setTitle(path.isEmpty() ? new File(directory).getName() : path.substring(path.lastIndexOf('/') + 1));
        binding.toolbar.setSubtitle(path.isEmpty() ? null : "/" + path);
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItem);
        setupSearch();

        adapter = new FileAdapter(new FileAdapter.Listener() {
            @Override
            public void onOpen(
                    @NonNull final RepoFiles.Entry entry
            ) {
                open(entry);
            }

            @Override
            public void onMore(
                    @NonNull final View anchor,
                    @NonNull final RepoFiles.Entry entry
            ) {
                showMenu(anchor, entry);
            }
        });
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null);

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.messages().observe(getViewLifecycleOwner(), this::showMessage);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.reload();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        adapter = null;
        super.onDestroyView();
    }

    private void render(
            @NonNull final FilesViewModel.UiState state
    ) {
        binding.progress.setVisibility(state.loading() ? View.VISIBLE : View.INVISIBLE);
        adapter.submitList(state.rows());
        final boolean nothingToShow = state.rows().isEmpty() && !state.loading();
        binding.empty.setVisibility(nothingToShow ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(nothingToShow ? View.GONE : View.VISIBLE);
        binding.emptyTitle.setText(state.empty() ? R.string.files_empty_title : R.string.files_no_match);
        binding.emptyBody.setVisibility(state.empty() ? View.VISIBLE : View.GONE);
    }

    private void setupSearch() {
        final MenuItem search = binding.toolbar.getMenu().findItem(R.id.action_search);
        final SearchView searchView = (SearchView) search.getActionView();
        searchView.setQueryHint(getString(R.string.files_search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(
                    final String query
            ) {
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(
                    final String newText
            ) {
                viewModel.setQuery(newText);
                return true;
            }
        });
        search.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(
                    @NonNull final MenuItem item
            ) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(
                    @NonNull final MenuItem item
            ) {
                viewModel.setQuery("");
                return true;
            }
        });
    }

    private boolean onMenuItem(
            @NonNull final MenuItem item
    ) {
        if (item.getItemId() == R.id.action_new_file) {
            askForName(R.string.files_new_file, R.string.files_name_file_hint, "", viewModel::createFile);
            return true;
        }
        if (item.getItemId() == R.id.action_new_folder) {
            askForName(R.string.files_new_folder, R.string.files_name_folder_hint, "", viewModel::createFolder);
            return true;
        }
        return false;
    }

    private void open(
            @NonNull final RepoFiles.Entry entry
    ) {
        final Bundle arguments = new Bundle();
        arguments.putString(ARG_DIRECTORY, viewModel.repoDirectory().getAbsolutePath());
        arguments.putString(entry.directory() ? ARG_PATH : FileViewerFragment.ARG_PATH, entry.path());
        NavHostFragment.findNavController(this).navigate(
                entry.directory() ? R.id.filesFragment : R.id.fileViewerFragment, arguments);
    }

    private void showMenu(
            @NonNull final View anchor,
            @NonNull final RepoFiles.Entry entry
    ) {
        final PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.inflate(R.menu.menu_file_item);
        menu.getMenu().findItem(R.id.item_open_external).setVisible(!entry.directory());
        menu.getMenu().findItem(R.id.item_share).setVisible(!entry.directory());
        menu.setOnMenuItemClickListener(item -> {
            final int id = item.getItemId();
            if (id == R.id.item_open_external) {
                withFile(entry, file -> {
                    if (!ExternalFiles.open(requireContext(), file)) {
                        Snackbar.make(binding.getRoot(), R.string.files_no_app, Snackbar.LENGTH_LONG).show();
                    }
                });
            } else if (id == R.id.item_share) {
                withFile(entry, file -> {
                    if (!ExternalFiles.share(requireContext(), file)) {
                        Snackbar.make(binding.getRoot(), R.string.files_no_app, Snackbar.LENGTH_LONG).show();
                    }
                });
            } else if (id == R.id.item_rename) {
                askForName(R.string.files_rename_title, entry.directory() ? R.string.files_name_folder_hint : R.string.files_name_file_hint,
                        entry.name(), name -> viewModel.rename(entry, name));
            } else if (id == R.id.item_delete) {
                confirmDelete(entry);
            } else {
                return false;
            }
            return true;
        });
        menu.show();
    }

    private void withFile(
            @NonNull final RepoFiles.Entry entry,
            @NonNull final Consumer<File> action
    ) {
        try {
            action.accept(viewModel.fileOf(entry));
        } catch (final RepoFiles.FilesException notAllowed) {
            Snackbar.make(binding.getRoot(), R.string.files_error_protected, Snackbar.LENGTH_LONG).show();
        }
    }

    private void confirmDelete(
            @NonNull final RepoFiles.Entry entry
    ) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.files_delete_title)
                .setMessage(getString(entry.directory() ? R.string.files_delete_folder_body : R.string.files_delete_file_body, entry.name()))
                .setPositiveButton(R.string.files_delete, (dialog, which) -> viewModel.delete(entry))
                .setNegativeButton(R.string.activity_cancel, null)
                .show();
    }

    private void askForName(
            final int title,
            final int hint,
            @NonNull final String initial,
            @NonNull final Consumer<String> onConfirm
    ) {
        final DialogNewFolderBinding dialogBinding = DialogNewFolderBinding.inflate(getLayoutInflater());
        dialogBinding.nameLayout.setHint(getString(hint));
        dialogBinding.nameInput.setText(initial);
        dialogBinding.nameInput.setSelection(initial.length());
        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.files_create, null)
                .setNegativeButton(R.string.activity_cancel, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            final String name = String.valueOf(dialogBinding.nameInput.getText()).strip();
            if (name.isEmpty()) {
                dialogBinding.nameLayout.setError(getString(R.string.files_error_invalid_name));
                return;
            }
            dialog.dismiss();
            onConfirm.accept(name);
        }));
        dialog.show();
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
