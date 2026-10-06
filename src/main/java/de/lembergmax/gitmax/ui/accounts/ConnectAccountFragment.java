package de.lembergmax.gitmax.ui.accounts;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.BuildConfig;
import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.FragmentConnectAccountBinding;
import de.lembergmax.gitmax.domain.TokenPage;
import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.InsetsPadding;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;

import java.util.Optional;

/**
 * Konto verbinden: Anbieter wählen, Token auf der Seite des Anbieters erstellen, einfügen und
 * prüfen lassen. Der Bildschirm ist gegen Screenshots und Bildschirmaufnahmen geschützt, weil ein
 * Token zu sehen sein kann.
 */
public final class ConnectAccountFragment extends Fragment {

    /** Schlüssel des Fragment-Ergebnisses, mit dem die Konten-Liste den Erfolg meldet. */
    public static final String RESULT_KEY = "account_connected";
    public static final String RESULT_LOGIN = "login";

    private FragmentConnectAccountBinding binding;
    private ConnectViewModel viewModel;

    public ConnectAccountFragment() {
        super(R.layout.fragment_connect_account);
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
        binding = FragmentConnectAccountBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(ConnectViewModel.class);

        Toolbars.setupBack(this, binding.toolbar);
        InsetsPadding.apply(binding.scroll, false, true, false);

        if (binding.providerToggle.getCheckedButtonId() == View.NO_ID) {
            binding.providerToggle.check(R.id.provider_github);
        }
        binding.providerToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                updateProviderTexts();
            }
        });
        binding.customServer.setOnCheckedChangeListener((button, checked) -> {
            binding.hostLayout.setVisibility(checked ? View.VISIBLE : View.GONE);
            binding.hostLayout.setError(null);
        });
        clearErrorOnEdit(binding.hostInput, () -> binding.hostLayout.setError(null));
        clearErrorOnEdit(binding.tokenInput, () -> binding.tokenLayout.setError(null));

        binding.createToken.setOnClickListener(button -> openTokenPage());
        binding.paste.setOnClickListener(button -> pasteToken());
        binding.submit.setOnClickListener(button -> submit());
        binding.tokenInput.setOnEditorActionListener((field, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });

        updateProviderTexts();
        viewModel.busy().observe(getViewLifecycleOwner(), this::showBusy);
        viewModel.outcome().observe(getViewLifecycleOwner(), this::handleOutcome);
    }

    @Override
    public void onResume() {
        super.onResume();
        // In den Prüfbuilds (debug, minified) bleibt der Schutz aus, damit Screenshots der Prüfläufe möglich sind.
        if (!BuildConfig.TEST_BUILD) {
            requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    public void onPause() {
        requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private ProviderType selectedProvider() {
        return binding.providerToggle.getCheckedButtonId() == R.id.provider_gitlab
                ? ProviderType.GITLAB
                : ProviderType.GITHUB;
    }

    private void updateProviderTexts() {
        final boolean github = selectedProvider() == ProviderType.GITHUB;
        binding.tokenHelp.setText(github ? R.string.connect_token_help_github : R.string.connect_token_help_gitlab);
        binding.customServer.setText(github ? R.string.connect_custom_server_github : R.string.connect_custom_server_gitlab);
        binding.hostLayout.setHint(getString(github ? R.string.connect_server_hint_github : R.string.connect_server_hint_gitlab));
    }

    private void submit() {
        hideKeyboard();
        binding.hostLayout.setError(null);
        binding.tokenLayout.setError(null);
        final Optional<AccountEndpoint> endpoint = AccountEndpoint.fromHostInput(
                selectedProvider(),
                binding.customServer.isChecked() ? String.valueOf(binding.hostInput.getText()) : null
        );
        if (endpoint.isPresent() && endpoint.get().isInsecure()) {
            confirmInsecure(endpoint.get());
            return;
        }
        connect(false);
    }

    /** Klartext-HTTP nur nach ausdrücklicher Bestätigung: Token und Code liefen sonst unverschlüsselt durchs Netz. */
    private void confirmInsecure(
            @NonNull final AccountEndpoint endpoint
    ) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.connect_insecure_title)
                .setMessage(getString(R.string.connect_insecure_body, endpoint.host()))
                .setNegativeButton(R.string.activity_cancel, null)
                .setPositiveButton(R.string.connect_insecure_confirm, (dialog, which) -> connect(true))
                .show();
    }

    private void connect(
            final boolean insecureConfirmed
    ) {
        viewModel.connect(
                selectedProvider(),
                binding.customServer.isChecked(),
                String.valueOf(binding.hostInput.getText()),
                String.valueOf(binding.tokenInput.getText()),
                insecureConfirmed
        );
    }

    private void openTokenPage() {
        final Optional<AccountEndpoint> endpoint = AccountEndpoint.fromHostInput(
                selectedProvider(),
                binding.customServer.isChecked() ? String.valueOf(binding.hostInput.getText()) : null
        );
        if (endpoint.isEmpty() || (binding.customServer.isChecked() && binding.hostInput.getText().toString().isBlank())) {
            binding.hostLayout.setError(getString(R.string.connect_error_host));
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TokenPage.url(endpoint.get()))));
        } catch (final ActivityNotFoundException noBrowser) {
            Snackbar.make(binding.getRoot(), R.string.connect_browser_missing, Snackbar.LENGTH_LONG).show();
        }
    }

    private void pasteToken() {
        final ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = clipboard.getPrimaryClip();
        final CharSequence text = clip != null && clip.getItemCount() > 0
                ? clip.getItemAt(0).coerceToText(requireContext())
                : null;
        if (text == null || text.toString().isBlank()) {
            Snackbar.make(binding.getRoot(), R.string.connect_clipboard_empty, Snackbar.LENGTH_SHORT).show();
            return;
        }
        binding.tokenInput.setText(text.toString().trim());
        binding.tokenInput.setSelection(binding.tokenInput.length());
    }

    private void showBusy(
            final boolean busy
    ) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        binding.submit.setEnabled(!busy);
        binding.submit.setText(busy ? R.string.connect_busy : R.string.connect_submit);
        binding.providerToggle.setEnabled(!busy);
        binding.providerGithub.setEnabled(!busy);
        binding.providerGitlab.setEnabled(!busy);
        binding.customServer.setEnabled(!busy);
        binding.hostLayout.setEnabled(!busy);
        binding.tokenLayout.setEnabled(!busy);
        binding.createToken.setEnabled(!busy);
        binding.paste.setEnabled(!busy);
    }

    private void handleOutcome(
            @Nullable final Event<ConnectViewModel.Outcome> event
    ) {
        final ConnectViewModel.Outcome outcome = event == null ? null : event.consume();
        if (outcome == null) {
            return;
        }
        if (outcome.success()) {
            final Bundle result = new Bundle();
            result.putString(RESULT_LOGIN, outcome.message());
            getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        switch (outcome.field()) {
            case HOST:
                binding.hostLayout.setError(outcome.message());
                break;
            case TOKEN:
                binding.tokenLayout.setError(outcome.message());
                break;
            case NONE:
            default:
                Snackbar.make(binding.getRoot(), outcome.message(), Snackbar.LENGTH_LONG).show();
                break;
        }
    }

    private void hideKeyboard() {
        WindowCompat.getInsetsController(requireActivity().getWindow(), binding.getRoot())
                .hide(WindowInsetsCompat.Type.ime());
    }

    private static void clearErrorOnEdit(
            @NonNull final android.widget.EditText field,
            @NonNull final Runnable clear
    ) {
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    final CharSequence text,
                    final int start,
                    final int count,
                    final int after
            ) {
                // nicht benötigt
            }

            @Override
            public void onTextChanged(
                    final CharSequence text,
                    final int start,
                    final int before,
                    final int count
            ) {
                clear.run();
            }

            @Override
            public void afterTextChanged(
                    final Editable editable
            ) {
                // nicht benötigt
            }
        });
    }
}
