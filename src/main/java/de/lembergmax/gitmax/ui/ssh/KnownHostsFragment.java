package de.lembergmax.gitmax.ui.ssh;

import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.databinding.FragmentSimpleListBinding;
import de.lembergmax.gitmax.domain.model.KnownHost;
import de.lembergmax.gitmax.ui.advanced.SimpleRow;
import de.lembergmax.gitmax.ui.advanced.SimpleRowAdapter;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;

import java.util.ArrayList;
import java.util.List;

/** Die SSH-Server, denen GitMax vertraut: Fingerabdruck ansehen und einen Server wieder vergessen. */
public final class KnownHostsFragment extends Fragment {

    private static final int MENU_FORGET = 1;

    private FragmentSimpleListBinding binding;
    private Model model;
    private SimpleRowAdapter adapter;

    public KnownHostsFragment() {
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
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentSimpleListBinding.bind(view);
        model = new ViewModelProvider(this).get(Model.class);

        Toolbars.setupBack(this, binding.toolbar);
        binding.toolbar.setTitle(R.string.ssh_hosts_title);
        binding.toolbar.getMenu().findItem(R.id.action_create).setVisible(false);
        binding.emptyTitle.setText(R.string.ssh_hosts_empty_title);
        binding.emptyBody.setText(R.string.ssh_hosts_empty_body);

        adapter = new SimpleRowAdapter(this::showActions, true);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null);

        model.hosts().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onResume() {
        super.onResume();
        model.reload();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        adapter = null;
        super.onDestroyView();
    }

    private void render(
            @NonNull final List<KnownHost> hosts
    ) {
        final List<SimpleRow> rows = new ArrayList<>();
        for (final KnownHost host : hosts) {
            final String trust = getString(host.verified() ? R.string.ssh_host_verified : R.string.ssh_host_trusted);
            rows.add(new SimpleRow(host.host() + "|" + host.fingerprint(), host.host(),
                    getString(R.string.ssh_host_subtitle, host.keyType(), host.fingerprint()) + "\n" + trust,
                    R.drawable.ic_lock, null, false, host));
        }
        adapter.submitList(rows);
        binding.empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showActions(
            @NonNull final View anchor,
            @NonNull final SimpleRow row
    ) {
        final KnownHost host = (KnownHost) row.payload();
        if (host == null) {
            return;
        }
        final PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, MENU_FORGET, 0, R.string.ssh_host_forget);
        menu.setOnMenuItemClickListener(item -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.ssh_host_forget_title)
                    .setMessage(getString(R.string.ssh_host_forget_body, host.host()))
                    .setPositiveButton(R.string.ssh_host_forget, (dialog, which) -> {
                        model.forget(host);
                        Snackbar.make(binding.getRoot(), R.string.ssh_host_forgotten, Snackbar.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.activity_cancel, null)
                    .show();
            return true;
        });
        menu.show();
    }

    /** Hält die bekannten Server. */
    public static final class Model extends AndroidViewModel {

        private final ServiceLocator services;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final MutableLiveData<List<KnownHost>> hosts = new MutableLiveData<>(List.of());

        public Model(
                @NonNull final Application application
        ) {
            super(application);
            this.services = ServiceLocator.from(application);
        }

        @NonNull
        LiveData<List<KnownHost>> hosts() {
            return hosts;
        }

        void reload() {
            services.io().execute(() -> {
                final List<KnownHost> all = services.knownHosts().all();
                main.post(() -> hosts.setValue(all));
            });
        }

        void forget(
                @NonNull final KnownHost host
        ) {
            services.io().execute(() -> {
                services.knownHosts().remove(host.host(), host.fingerprint());
                final List<KnownHost> all = services.knownHosts().all();
                main.post(() -> hosts.setValue(all));
            });
        }
    }
}
