package de.lembergmax.gitmax.ui.accounts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.BuildConfig;
import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.DialogTokenBinding;
import de.lembergmax.gitmax.databinding.FragmentAccountDetailBinding;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.InsetsPadding;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;

/**
 * Ein Konto: Commit-Identität bearbeiten, Rechte des Tokens ansehen, Token erneuern, Konto entfernen.
 */
public final class AccountDetailFragment extends Fragment {

    private FragmentAccountDetailBinding binding;
    private AccountDetailViewModel viewModel;
    private Account shown;
    private boolean identityFilled;

    @Nullable
    private AlertDialog renewDialog;
    @Nullable
    private DialogTokenBinding renewBinding;

    public AccountDetailFragment() {
        super(R.layout.fragment_account_detail);
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
        binding = FragmentAccountDetailBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(AccountDetailViewModel.class);

        Toolbars.setupBack(this, binding.toolbar);
        InsetsPadding.apply(binding.scroll, false, true, false);

        binding.saveIdentity.setOnClickListener(button -> viewModel.saveIdentity(
                String.valueOf(binding.nameInput.getText()),
                String.valueOf(binding.emailInput.getText())
        ));
        binding.renewToken.setOnClickListener(button -> showRenewDialog());
        binding.removeAccount.setOnClickListener(button -> showRemoveDialog());

        viewModel.account().observe(getViewLifecycleOwner(), this::render);
        viewModel.missing().observe(getViewLifecycleOwner(), missing -> {
            if (Boolean.TRUE.equals(missing)) {
                NavHostFragment.findNavController(this).navigateUp();
            }
        });
        viewModel.busy().observe(getViewLifecycleOwner(), busy ->
                binding.progress.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.INVISIBLE));
        viewModel.results().observe(getViewLifecycleOwner(), this::handleResult);

        final String accountId = requireArguments().getString(AccountsFragment.ARG_ACCOUNT_ID);
        if (accountId == null) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        viewModel.load(accountId);
    }

    @Override
    public void onDestroyView() {
        if (renewDialog != null) {
            renewDialog.dismiss();
            renewDialog = null;
            renewBinding = null;
        }
        binding = null;
        identityFilled = false;
        super.onDestroyView();
    }

    private void render(
            @Nullable final Account account
    ) {
        if (account == null) {
            return;
        }
        shown = account;
        final ProviderType provider = account.endpoint().provider();
        binding.toolbar.setTitle(account.login());
        binding.tile.setText(provider == ProviderType.GITHUB ? "GH" : "GL");
        binding.login.setText(account.login());
        binding.server.setText(getString(R.string.accounts_row_description, provider.displayName(), account.endpoint().host()));

        if (!identityFilled) {
            binding.nameInput.setText(account.identity().name());
            binding.emailInput.setText(account.identity().email());
            identityFilled = true;
        }

        renderBanner(account.status());
        renderScopes(account);
    }

    private void renderBanner(
            @NonNull final Account.Status status
    ) {
        switch (status) {
            case TOKEN_REJECTED:
                binding.statusBanner.setText(R.string.account_detail_banner_rejected);
                binding.statusBanner.setVisibility(View.VISIBLE);
                break;
            case SECRET_LOST:
                binding.statusBanner.setText(R.string.account_detail_banner_secret_lost);
                binding.statusBanner.setVisibility(View.VISIBLE);
                break;
            case ACTIVE:
            default:
                binding.statusBanner.setVisibility(View.GONE);
                break;
        }
    }

    private void renderScopes(
            @NonNull final Account account
    ) {
        binding.scopes.removeAllViews();
        binding.scopesUnknown.setVisibility(account.scopes().isEmpty() ? View.VISIBLE : View.GONE);
        for (final String scope : account.scopes()) {
            final Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
            chip.setText(scope);
            chip.setClickable(false);
            chip.setCheckable(false);
            binding.scopes.addView(chip);
        }
    }

    private void handleResult(
            @Nullable final Event<AccountDetailViewModel.Result> event
    ) {
        final AccountDetailViewModel.Result result = event == null ? null : event.consume();
        if (result == null) {
            return;
        }
        switch (result.kind()) {
            case IDENTITY_SAVED:
                Snackbar.make(binding.getRoot(), R.string.account_detail_saved, Snackbar.LENGTH_SHORT).show();
                break;
            case NAME_INVALID:
                binding.nameLayout.setError(getString(R.string.account_detail_error_name));
                break;
            case EMAIL_INVALID:
                binding.emailLayout.setError(getString(R.string.account_detail_error_email));
                break;
            case RENEWED:
                dismissRenewDialog();
                Snackbar.make(binding.getRoot(), R.string.account_detail_renewed, Snackbar.LENGTH_SHORT).show();
                break;
            case RENEW_FAILED:
                if (renewBinding != null) {
                    renewBinding.tokenLayout.setError(result.text());
                } else {
                    Snackbar.make(binding.getRoot(), result.text(), Snackbar.LENGTH_LONG).show();
                }
                break;
            case REMOVED:
                NavHostFragment.findNavController(this).navigateUp();
                break;
            default:
                break;
        }
    }

    private void showRenewDialog() {
        if (shown == null) {
            return;
        }
        renewBinding = DialogTokenBinding.inflate(LayoutInflater.from(requireContext()));
        renewDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_detail_renew_title)
                .setMessage(getString(R.string.account_detail_renew_body, shown.login()))
                .setView(renewBinding.getRoot())
                .setNegativeButton(R.string.account_detail_cancel, null)
                .setPositiveButton(R.string.account_detail_renew_confirm, null)
                .create();
        if (!BuildConfig.TEST_BUILD) {
            renewDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        renewDialog.setOnDismissListener(dialog -> {
            renewDialog = null;
            renewBinding = null;
        });
        renewDialog.show();
        // Der Knopf schließt den Dialog nicht von selbst: bei einem Fehler soll er offen bleiben.
        renewDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            renewBinding.tokenLayout.setError(null);
            viewModel.renewToken(String.valueOf(renewBinding.tokenInput.getText()));
        });
    }

    private void dismissRenewDialog() {
        if (renewDialog != null) {
            renewDialog.dismiss();
        }
    }

    private void showRemoveDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_detail_remove_title)
                .setMessage(R.string.account_detail_remove_body)
                .setNegativeButton(R.string.account_detail_cancel, null)
                .setPositiveButton(R.string.account_detail_remove_confirm, (dialog, which) -> viewModel.remove())
                .show();
    }
}
