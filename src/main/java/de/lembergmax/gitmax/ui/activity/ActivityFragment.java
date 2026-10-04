package de.lembergmax.gitmax.ui.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.databinding.FragmentActivityBinding;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.ops.OperationEntry;
import de.lembergmax.gitmax.ops.OperationTexts;
import de.lembergmax.gitmax.ui.common.HostKeyDialog;
import de.lembergmax.gitmax.ui.common.Motion;

import java.util.List;

/** Zeigt laufende, wartende und beendete Git-Vorgänge mit Fortschritt, Ergebnis und Fehlerdetails. */
public final class ActivityFragment extends Fragment {

    private FragmentActivityBinding binding;
    private ActivityViewModel viewModel;
    private OperationAdapter adapter;

    public ActivityFragment() {
        super(R.layout.fragment_activity);
    }

    @Override
    public void onCreate(
            @Nullable final Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        setEnterTransition(Motion.fadeThrough(requireContext()));
        setExitTransition(Motion.fadeThrough(requireContext()));
        setReenterTransition(Motion.fadeThrough(requireContext()));
        setReturnTransition(Motion.fadeThrough(requireContext()));
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentActivityBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(ActivityViewModel.class);

        adapter = new OperationAdapter(new OperationAdapter.Listener() {
            @Override
            public void onOpen(
                    @NonNull final OperationEntry entry,
                    final boolean retryable
            ) {
                showDetails(entry, retryable);
            }

            @Override
            public void onCancel(
                    @NonNull final OperationEntry entry
            ) {
                viewModel.cancel(entry);
            }

            @Override
            public void onRetry(
                    @NonNull final OperationEntry entry
            ) {
                viewModel.retry(entry);
            }
        });
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null);

        binding.toolbar.setOnMenuItemClickListener(this::onMenuItem);
        binding.emptyAction.setOnClickListener(button ->
                NavHostFragment.findNavController(this).navigate(R.id.discoverFragment));

        viewModel.items().observe(getViewLifecycleOwner(), this::render);
        viewModel.hasActive().observe(getViewLifecycleOwner(), active ->
                binding.toolbar.getMenu().findItem(R.id.action_cancel_all).setVisible(Boolean.TRUE.equals(active)));
        viewModel.hasFinished().observe(getViewLifecycleOwner(), finished ->
                binding.toolbar.getMenu().findItem(R.id.action_clear).setVisible(Boolean.TRUE.equals(finished)));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        adapter = null;
        super.onDestroyView();
    }

    private void render(
            @NonNull final List<ActivityViewModel.Item> items
    ) {
        adapter.submitList(items);
        binding.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private boolean onMenuItem(
            @NonNull final MenuItem item
    ) {
        if (item.getItemId() == R.id.action_cancel_all) {
            viewModel.cancelAll();
            return true;
        }
        if (item.getItemId() == R.id.action_clear) {
            viewModel.clearFinished();
            return true;
        }
        return false;
    }

    private void showDetails(
            @NonNull final OperationEntry entry,
            final boolean retryable
    ) {
        final Context context = requireContext();
        final StringBuilder message = new StringBuilder(OperationTexts.status(context, entry));
        final OperationEntry.Failure failure = entry.failure();
        final String technical = failure == null ? "" : technicalDetails(failure);
        if (!technical.isEmpty()) {
            message.append("\n\n").append(getString(R.string.activity_details_technical)).append('\n').append(technical);
        }
        final MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(getString(R.string.activity_details_title, OperationTexts.kind(context, entry.kind()), entry.title()))
                .setMessage(message.toString())
                .setPositiveButton(R.string.activity_close, null);
        if (!technical.isEmpty()) {
            dialog.setNeutralButton(R.string.activity_copy_details, (d, which) -> copy(technical));
        }
        if (failure != null && (failure.kind() == GitFailureKind.HOST_KEY_UNKNOWN || failure.kind() == GitFailureKind.HOST_KEY_CHANGED)) {
            final String host = failure.paths().isEmpty() ? "" : failure.paths().get(0);
            dialog.setNegativeButton(R.string.activity_check_server, (d, which) ->
                    HostKeyDialog.show(context, ServiceLocator.from(context).knownHosts(), host, binding.getRoot(),
                            () -> viewModel.retry(entry)));
        } else if (retryable) {
            dialog.setNegativeButton(R.string.activity_retry, (d, which) -> viewModel.retry(entry));
        }
        dialog.show();
    }

    private static String technicalDetails(
            @NonNull final OperationEntry.Failure failure
    ) {
        final StringBuilder details = new StringBuilder(failure.message());
        for (final String path : failure.paths()) {
            details.append('\n').append(path);
        }
        return details.toString();
    }

    private void copy(
            @NonNull final String text
    ) {
        final ClipboardManager clipboard = requireContext().getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.activity_details_technical), text));
        Snackbar.make(binding.getRoot(), R.string.activity_copied, Snackbar.LENGTH_SHORT).show();
    }
}
