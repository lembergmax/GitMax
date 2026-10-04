package de.lembergmax.gitmax.ui.viewer;

import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.transition.MaterialSharedAxis;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.ServiceLocator;
import de.lembergmax.gitmax.databinding.FragmentSimpleListBinding;
import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.BlameLine;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.ui.advanced.CommitDetailFragment;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.Toolbars;

import java.io.File;
import java.util.List;

/** Blame: zu jeder Zeile einer Datei der Commit, der sie zuletzt geändert hat. Ein Tipp auf den Kopf öffnet den Commit. */
public final class BlameFragment extends Fragment {

    /** Pfad des Repos. */
    public static final String ARG_DIRECTORY = "directory";

    /** Pfad der Datei im Repo, relativ und mit {@code /}. */
    public static final String ARG_PATH = "path";

    private FragmentSimpleListBinding binding;
    private Model model;
    private BlameAdapter adapter;

    public BlameFragment() {
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
        final String directory = requireArguments().getString(ARG_DIRECTORY);
        final String path = requireArguments().getString(ARG_PATH);
        if (directory == null || path == null) {
            throw new IllegalStateException("No file passed");
        }
        model.init(directory, path);

        Toolbars.setupBack(this, binding.toolbar);
        binding.toolbar.setTitle(R.string.blame_title);
        binding.toolbar.setSubtitle(new File(path).getName());
        binding.toolbar.getMenu().findItem(R.id.action_create).setVisible(false);

        adapter = new BlameAdapter(line -> openCommit(directory, line));
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null);

        model.state().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        adapter = null;
        super.onDestroyView();
    }

    private void render(
            @NonNull final State state
    ) {
        binding.progress.setVisibility(state.loading() ? View.VISIBLE : View.INVISIBLE);
        adapter.submitList(BlameAdapter.itemsOf(state.lines()));
        final boolean empty = state.lines().isEmpty() && !state.loading();
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.emptyTitle.setText(state.failure() == null ? R.string.blame_empty_title : titleOf(state.failure()));
        binding.emptyBody.setText(state.failure() == null ? R.string.blame_empty_body : bodyOf(state.failure()));
    }

    private static int titleOf(
            @NonNull final GitFailureKind kind
    ) {
        return kind == GitFailureKind.INVALID_PATH ? R.string.blame_too_large_title : R.string.blame_empty_title;
    }

    private static int bodyOf(
            @NonNull final GitFailureKind kind
    ) {
        return kind == GitFailureKind.INVALID_PATH ? R.string.blame_too_large_body : R.string.blame_empty_body;
    }

    private void openCommit(
            @NonNull final String directory,
            @NonNull final BlameLine line
    ) {
        final Bundle arguments = new Bundle();
        arguments.putString(CommitDetailFragment.ARG_DIRECTORY, directory);
        arguments.putString(CommitDetailFragment.ARG_COMMIT, line.commitId());
        NavHostFragment.findNavController(this).navigate(R.id.commitDetailFragment, arguments);
    }

    /**
     * Zustand des Bildschirms.
     *
     * @param loading die Zeilen werden berechnet
     * @param lines   die Zeilen mit ihren Commits
     * @param failure Art des Fehlers, wenn die Berechnung scheiterte, sonst {@code null}
     */
    public record State(
            boolean loading,
            @NonNull List<BlameLine> lines,
            @Nullable GitFailureKind failure
    ) {
    }

    /** Berechnet Blame im Hintergrund. */
    public static final class Model extends AndroidViewModel {

        private final ServiceLocator services;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final MutableLiveData<State> state = new MutableLiveData<>(new State(true, List.of(), null));
        private boolean started;

        public Model(
                @NonNull final Application application
        ) {
            super(application);
            this.services = ServiceLocator.from(application);
        }

        @NonNull
        LiveData<State> state() {
            return state;
        }

        void init(
                @NonNull final String directory,
                @NonNull final String path
        ) {
            if (started) {
                return;
            }
            started = true;
            services.io().execute(() -> {
                State result;
                try {
                    result = new State(false, services.advanced().blame(new File(directory), path), null);
                } catch (final GitFailureException failure) {
                    result = new State(false, List.of(), failure.kind());
                }
                final State loaded = result;
                main.post(() -> state.setValue(loaded));
            });
        }
    }
}
