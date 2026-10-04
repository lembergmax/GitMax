package de.lembergmax.gitmax.ui.accounts;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.FragmentAccountsBinding;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;

import java.util.List;

/**
 * Liste der verknüpften Konten mit dem Einstieg zum Verbinden eines neuen.
 */
public final class AccountsFragment extends Fragment {

    public static final String ARG_ACCOUNT_ID = "accountId";

    private FragmentAccountsBinding binding;
    private AccountsViewModel viewModel;
    private AccountAdapter adapter;

    public AccountsFragment() {
        super(R.layout.fragment_accounts);
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
        binding = FragmentAccountsBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(AccountsViewModel.class);

        Toolbars.setupBack(this, binding.toolbar);

        adapter = new AccountAdapter(this::openDetail);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);

        binding.fabConnect.setOnClickListener(button -> openConnect());
        binding.emptyConnect.setOnClickListener(button -> openConnect());

        viewModel.accounts().observe(getViewLifecycleOwner(), this::showAccounts);
        getParentFragmentManager().setFragmentResultListener(
                ConnectAccountFragment.RESULT_KEY,
                getViewLifecycleOwner(),
                (key, result) -> Snackbar.make(
                        binding.getRoot(),
                        getString(R.string.connect_success, result.getString(ConnectAccountFragment.RESULT_LOGIN, "")),
                        Snackbar.LENGTH_LONG
                ).show()
        );
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

    private void showAccounts(
            @NonNull final List<Account> accounts
    ) {
        adapter.submitList(accounts);
        final boolean empty = accounts.isEmpty();
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(empty ? View.GONE : View.VISIBLE);
        // Genau eine Hauptaktion je Bildschirm: leer führt der Knopf im Leerzustand, sonst der FAB.
        if (empty) {
            binding.fabConnect.hide();
        } else {
            binding.fabConnect.show();
        }
    }

    private void openDetail(
            @NonNull final Account account
    ) {
        final Bundle arguments = new Bundle();
        arguments.putString(ARG_ACCOUNT_ID, account.id());
        NavHostFragment.findNavController(this).navigate(R.id.accountDetailFragment, arguments);
    }

    private void openConnect() {
        NavHostFragment.findNavController(this).navigate(R.id.connectAccountFragment);
    }
}
