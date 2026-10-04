package de.lembergmax.gitmax.ui.ssh;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.FragmentSimpleListBinding;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.domain.model.SshKeyInfo;
import de.lembergmax.gitmax.domain.model.SshKeyType;
import de.lembergmax.gitmax.ui.advanced.ListSource;
import de.lembergmax.gitmax.ui.advanced.PromptDialog;
import de.lembergmax.gitmax.ui.advanced.SimpleRow;
import de.lembergmax.gitmax.ui.advanced.SimpleRowAdapter;
import de.lembergmax.gitmax.ui.common.Event;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Die SSH-Schlüssel der App: erzeugen, importieren, den öffentlichen Teil kopieren oder teilen, beim Anbieter
 * hinterlegen und löschen. Der private Schlüssel ist nirgends zu sehen und verlässt den Tresor nie.
 */
public final class SshKeysFragment extends Fragment {

    private static final int MENU_COPY = 1;
    private static final int MENU_SHARE = 2;
    private static final int MENU_DELETE = 3;
    private static final int MENU_REGISTER_BASE = 100;

    private FragmentSimpleListBinding binding;
    private SshKeysViewModel viewModel;
    private SimpleRowAdapter adapter;
    private ActivityResultLauncher<String[]> picker;

    public SshKeysFragment() {
        super(R.layout.fragment_simple_list);
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
        picker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFilePicked);
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentSimpleListBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(SshKeysViewModel.class);

        Toolbars.setupBack(this, binding.toolbar);
        binding.toolbar.setTitle(R.string.ssh_keys_title);
        binding.toolbar.getMenu().clear();
        binding.toolbar.inflateMenu(R.menu.menu_ssh_keys);
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItem);
        binding.emptyTitle.setText(R.string.ssh_keys_empty_title);
        binding.emptyBody.setText(R.string.ssh_keys_empty_body);

        adapter = new SimpleRowAdapter(this::showActions, true);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null);

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.notices().observe(getViewLifecycleOwner(), this::showNotice);
        viewModel.importRequests().observe(getViewLifecycleOwner(), this::askForImport);
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
            @NonNull final SshKeysViewModel.UiState state
    ) {
        binding.progress.setVisibility(state.loading() ? View.VISIBLE : View.INVISIBLE);
        final List<SimpleRow> rows = new ArrayList<>();
        for (final SshKeyInfo key : state.keys()) {
            rows.add(new SimpleRow(key.id(), key.label(),
                    getString(R.string.ssh_key_subtitle, typeName(key.type()), key.fingerprint()),
                    R.drawable.ic_key, null, false, key));
        }
        adapter.submitList(rows);
        final boolean empty = rows.isEmpty() && !state.loading();
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private String typeName(
            @NonNull final SshKeyType type
    ) {
        switch (type) {
            case RSA:
                return getString(R.string.ssh_key_type_rsa);
            case ECDSA:
                return getString(R.string.ssh_key_type_ecdsa);
            case ED25519:
            default:
                return getString(R.string.ssh_key_type_ed25519);
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Menü der Kopfleiste                                                                        */
    /* ------------------------------------------------------------------------------------------ */

    private boolean onMenuItem(
            @NonNull final MenuItem item
    ) {
        final int id = item.getItemId();
        if (id == R.id.action_create_key) {
            PromptDialog.show(requireContext(), getLayoutInflater(), new ListSource.Prompt(R.string.ssh_keys_create, R.string.files_create,
                    List.of(new ListSource.Field(R.string.ssh_key_name_hint, "", false, true)),
                    List.of(new ListSource.Toggle(R.string.ssh_key_rsa_toggle, false))),
                    values -> viewModel.generate(values.field(0), values.toggle(0) ? SshKeyType.RSA : SshKeyType.ED25519));
        } else if (id == R.id.action_import_key) {
            picker.launch(new String[] {"*/*"});
        } else if (id == R.id.action_known_hosts) {
            NavHostFragment.findNavController(this).navigate(R.id.knownHostsFragment);
        } else {
            return false;
        }
        return true;
    }

    private void onFilePicked(
            @Nullable final Uri uri
    ) {
        if (uri != null) {
            viewModel.prepareImport(uri, displayName(uri));
        }
    }

    private String displayName(
            @NonNull final Uri uri
    ) {
        try (Cursor cursor = requireContext().getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final String name = cursor.getString(0);
                return name == null ? "" : name;
            }
        }
        return "";
    }

    private void askForImport(
            @Nullable final Event<SshKeysViewModel.ImportRequest> event
    ) {
        final SshKeysViewModel.ImportRequest request = event == null ? null : event.consume();
        if (request == null) {
            return;
        }
        final List<ListSource.Field> fields = new ArrayList<>();
        fields.add(new ListSource.Field(R.string.ssh_key_name_hint, request.suggestedName(), false, true));
        if (request.needsPassphrase()) {
            fields.add(new ListSource.Field(R.string.ssh_key_passphrase_hint, "", false, true, true));
        }
        PromptDialog.show(requireContext(), getLayoutInflater(), new ListSource.Prompt(R.string.ssh_key_import_title,
                R.string.ssh_key_import_confirm, fields, List.of()),
                values -> viewModel.importPending(values.field(0), request.needsPassphrase() ? values.field(1) : null));
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Aktionen je Schlüssel                                                                      */
    /* ------------------------------------------------------------------------------------------ */

    private void showActions(
            @NonNull final View anchor,
            @NonNull final SimpleRow row
    ) {
        final SshKeyInfo key = (SshKeyInfo) row.payload();
        if (key == null) {
            return;
        }
        final PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, MENU_COPY, 0, R.string.ssh_key_copy);
        menu.getMenu().add(0, MENU_SHARE, 1, R.string.ssh_key_share);
        final List<String> hosts = registerHosts();
        for (int index = 0; index < hosts.size(); index += 1) {
            menu.getMenu().add(0, MENU_REGISTER_BASE + index, 2 + index, getString(R.string.ssh_key_register, hosts.get(index)));
        }
        menu.getMenu().add(0, MENU_DELETE, 100, R.string.ssh_key_delete);
        menu.setOnMenuItemClickListener(item -> {
            perform(key, hosts, item.getItemId());
            return true;
        });
        menu.show();
    }

    private void perform(
            @NonNull final SshKeyInfo key,
            @NonNull final List<String> hosts,
            final int action
    ) {
        if (action == MENU_COPY) {
            copy(key);
            Snackbar.make(binding.getRoot(), R.string.ssh_key_copied, Snackbar.LENGTH_SHORT).show();
        } else if (action == MENU_SHARE) {
            final Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, key.publicKey());
            startActivity(Intent.createChooser(send, getString(R.string.ssh_key_share)));
        } else if (action == MENU_DELETE) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.ssh_key_delete_title)
                    .setMessage(getString(R.string.ssh_key_delete_body, key.label()))
                    .setPositiveButton(R.string.ssh_key_delete, (dialog, which) -> viewModel.remove(key))
                    .setNegativeButton(R.string.activity_cancel, null)
                    .show();
        } else if (action >= MENU_REGISTER_BASE) {
            register(key, hosts.get(action - MENU_REGISTER_BASE));
        }
    }

    private void copy(
            @NonNull final SshKeyInfo key
    ) {
        final ClipboardManager clipboard = requireContext().getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.ssh_key_copy), key.publicKey()));
    }

    /** Die Hosts der verknüpften Konten; ohne Konto die öffentlichen Seiten von GitHub und GitLab. */
    private List<String> registerHosts() {
        final Set<String> hosts = new LinkedHashSet<>();
        for (final Account account : viewModel.accounts()) {
            hosts.add(account.endpoint().host());
        }
        if (hosts.isEmpty()) {
            hosts.add(ProviderType.GITHUB.defaultHost());
            hosts.add(ProviderType.GITLAB.defaultHost());
        }
        return new ArrayList<>(hosts);
    }

    /** Kopiert den öffentlichen Schlüssel und öffnet die Seite, auf der der Anbieter ihn entgegennimmt. */
    private void register(
            @NonNull final SshKeyInfo key,
            @NonNull final String host
    ) {
        copy(key);
        final boolean github = viewModel.accounts().stream()
                .filter(account -> account.endpoint().host().equals(host))
                .anyMatch(account -> account.endpoint().provider() == ProviderType.GITHUB)
                || host.equals(ProviderType.GITHUB.defaultHost());
        final String path = github ? "/settings/ssh/new" : "/-/user_settings/ssh_keys";
        Snackbar.make(binding.getRoot(), R.string.ssh_key_register_hint, Snackbar.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://" + host + path)));
        } catch (final ActivityNotFoundException noBrowser) {
            // Ohne Browser bleibt der Hinweis: Der Schlüssel liegt in der Zwischenablage und die Seite lässt sich anders öffnen.
        }
    }

    private void showNotice(
            @Nullable final Event<SshKeysViewModel.Notice> event
    ) {
        final SshKeysViewModel.Notice notice = event == null ? null : event.consume();
        if (notice != null) {
            Snackbar.make(binding.getRoot(), notice.text(), notice.error() ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT).show();
        }
    }
}
