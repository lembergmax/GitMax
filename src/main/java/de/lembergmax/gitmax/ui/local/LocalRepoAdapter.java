package de.lembergmax.gitmax.ui.local;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.ItemLocalRepoBinding;
import de.lembergmax.gitmax.domain.model.RepoHealth;
import de.lembergmax.gitmax.domain.model.RepoStatus;
import de.lembergmax.gitmax.git.GitProgress;
import de.lembergmax.gitmax.ops.OperationEntry;
import de.lembergmax.gitmax.ops.OperationState;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.PebbleView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Zeigt lokale Repos: Kieselstein mit Zustand, Name, Pfad und die Angaben Branch, voraus, zurück
 * und geändert.
 */
final class LocalRepoAdapter extends ListAdapter<LocalViewModel.Row, LocalRepoAdapter.Holder> {

    /** Reaktionen auf Antippen und langes Drücken einer Zeile. */
    interface Listener {

        void onRepoClick(
                @NonNull LocalViewModel.Row row
        );

        void onRepoLongClick(
                @NonNull LocalViewModel.Row row
        );
    }

    private static final Object PAYLOAD_SELECTION = new Object();

    private static final int FACT_ICON_DP = 16;

    private static final DiffUtil.ItemCallback<LocalViewModel.Row> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(
                @NonNull final LocalViewModel.Row oldItem,
                @NonNull final LocalViewModel.Row newItem
        ) {
            return oldItem.key().equals(newItem.key());
        }

        @Override
        public boolean areContentsTheSame(
                @NonNull final LocalViewModel.Row oldItem,
                @NonNull final LocalViewModel.Row newItem
        ) {
            return oldItem.equals(newItem);
        }

        @Override
        public Object getChangePayload(
                @NonNull final LocalViewModel.Row oldItem,
                @NonNull final LocalViewModel.Row newItem
        ) {
            final boolean onlySelectionChanged = oldItem.selected() != newItem.selected()
                    && oldItem.repo().equals(newItem.repo())
                    && Objects.equals(oldItem.status(), newItem.status())
                    && oldItem.unreadable() == newItem.unreadable()
                    && Objects.equals(oldItem.active(), newItem.active());
            return onlySelectionChanged ? PAYLOAD_SELECTION : null;
        }
    };

    private final Listener listener;

    LocalRepoAdapter(
            @NonNull final Listener listener
    ) {
        super(DIFF);
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(
            @NonNull final ViewGroup parent,
            final int viewType
    ) {
        return new Holder(ItemLocalRepoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(
            @NonNull final Holder holder,
            final int position
    ) {
        holder.bind(getItem(position), listener, false);
    }

    @Override
    public void onBindViewHolder(
            @NonNull final Holder holder,
            final int position,
            @NonNull final List<Object> payloads
    ) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.showSelection(getItem(position), true);
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    /** Eine Repo-Zeile. */
    static final class Holder extends RecyclerView.ViewHolder {

        private final ItemLocalRepoBinding binding;

        Holder(
                @NonNull final ItemLocalRepoBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
            setFactIcon(binding.branch, R.drawable.ic_fork_right);
            setFactIcon(binding.ahead, R.drawable.ic_arrow_upward);
            setFactIcon(binding.behind, R.drawable.ic_arrow_downward);
            setFactIcon(binding.changed, R.drawable.ic_edit);
        }

        void bind(
                @NonNull final LocalViewModel.Row row,
                @NonNull final Listener listener,
                final boolean animate
        ) {
            final Context context = binding.getRoot().getContext();
            binding.name.setText(row.repo().name());
            // Der Pfad ist nur eine Zusatzinformation, wenn er mehr sagt als der Name (Repo in einer Gruppe).
            final String path = row.repo().relativePath();
            binding.path.setText(path);
            binding.path.setVisibility(path.equals(row.repo().name()) ? View.GONE : View.VISIBLE);

            final RepoStatus status = row.status();
            bindPebble(row, status);
            bindFacts(context, row);
            showSelection(row, animate);

            binding.getRoot().setContentDescription(describe(context, row));
            binding.getRoot().setOnClickListener(view -> listener.onRepoClick(row));
            binding.getRoot().setOnLongClickListener(view -> {
                listener.onRepoLongClick(row);
                return true;
            });
        }

        void showSelection(
                @NonNull final LocalViewModel.Row row,
                final boolean animate
        ) {
            Motion.swapIcon(binding.check, row.selected(), animate);
            ViewCompat.setStateDescription(binding.getRoot(), row.selected()
                    ? binding.getRoot().getContext().getString(R.string.discover_row_selected) : null);
        }

        private void bindPebble(
                final LocalViewModel.Row row,
                final RepoStatus status
        ) {
            final OperationEntry active = row.active();
            if (active != null && active.state() == OperationState.RUNNING) {
                binding.pebble.setStatus(PebbleView.Status.RUNNING);
                final OperationEntry.Progress progress = active.progress();
                if (progress == null || progress.fraction() == GitProgress.UNKNOWN) {
                    binding.pebble.setIndeterminate(true);
                } else {
                    binding.pebble.setProgress(progress.fraction());
                }
                return;
            }
            if (status == null) {
                // Ein Repo, dessen Zustand sich nicht lesen ließ, darf nicht wie ein sauberes aussehen.
                binding.pebble.setStatus(row.unreadable() ? PebbleView.Status.FAILED : PebbleView.Status.CLEAN);
                return;
            }
            binding.pebble.setStatus(pebbleFor(status.health()));
        }

        private void bindFacts(
                final Context context,
                final LocalViewModel.Row row
        ) {
            final RepoStatus status = row.status();
            if (status == null) {
                show(binding.branch, row.unreadable() ? context.getString(R.string.local_row_unreadable) : "");
                hide(binding.ahead);
                hide(binding.behind);
                hide(binding.changed);
                return;
            }
            show(binding.branch, status.detached()
                    ? context.getString(R.string.local_row_detached, status.branch())
                    : status.branch());
            showCount(binding.ahead, status.ahead());
            showCount(binding.behind, status.behind());
            showCount(binding.changed, status.changesKnown() ? status.changed() : 0);
        }

        private static void showCount(
                final TextView view,
                final int count
        ) {
            if (count > 0) {
                show(view, String.valueOf(count));
            } else {
                hide(view);
            }
        }

        private static void show(
                final TextView view,
                final String text
        ) {
            view.setText(text);
            view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        }

        private static void hide(
                final TextView view
        ) {
            view.setVisibility(View.GONE);
        }

        private void setFactIcon(
                final TextView view,
                @DrawableRes final int icon
        ) {
            final Drawable drawable = AppCompatResources.getDrawable(view.getContext(), icon);
            if (drawable == null) {
                return;
            }
            final int size = Math.round(FACT_ICON_DP * view.getResources().getDisplayMetrics().density);
            final Drawable sized = drawable.mutate();
            sized.setBounds(0, 0, size, size);
            view.setCompoundDrawablesRelative(sized, null, null, null);
        }

        private static PebbleView.Status pebbleFor(
                final RepoHealth health
        ) {
            switch (health) {
                case CONFLICT:
                    return PebbleView.Status.CONFLICT;
                case CHANGED:
                    return PebbleView.Status.CHANGED;
                case DIVERGED:
                    return PebbleView.Status.DIVERGED;
                case BEHIND:
                    return PebbleView.Status.BEHIND;
                case AHEAD:
                    return PebbleView.Status.AHEAD;
                case CLEAN:
                default:
                    return PebbleView.Status.CLEAN;
            }
        }

        /** Sprechbarer Zustand für TalkBack: Farbe und Bogen allein tragen die Information nicht. */
        private static String describe(
                final Context context,
                final LocalViewModel.Row row
        ) {
            final List<String> parts = new ArrayList<>();
            parts.add(row.repo().name());
            final RepoStatus status = row.status();
            if (status == null) {
                parts.add(context.getString(R.string.local_row_unreadable));
                return String.join(", ", parts);
            }
            parts.add(status.branch());
            if (status.conflicted()) {
                parts.add(context.getString(R.string.local_row_conflict));
            }
            if (status.changed() > 0) {
                parts.add(context.getResources().getQuantityString(R.plurals.local_row_changed, status.changed(), status.changed()));
            }
            if (status.ahead() > 0) {
                parts.add(context.getResources().getQuantityString(R.plurals.local_row_ahead, status.ahead(), status.ahead()));
            }
            if (status.behind() > 0) {
                parts.add(context.getResources().getQuantityString(R.plurals.local_row_behind, status.behind(), status.behind()));
            }
            if (status.health() == RepoHealth.CLEAN) {
                parts.add(context.getString(R.string.local_row_clean));
            }
            return String.join(", ", parts);
        }
    }
}
