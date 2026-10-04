package de.lembergmax.gitmax.ui.advanced;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.ItemCommitBinding;
import de.lembergmax.gitmax.domain.model.CommitInfo;
import de.lembergmax.gitmax.domain.model.RefLabel;
import de.lembergmax.gitmax.ui.common.RelativeTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Zeigt Commits mit Betreff, Autor, Zeit, Kennung und den Marken, die auf sie zeigen. */
final class CommitAdapter extends ListAdapter<CommitInfo, CommitAdapter.Holder> {

    /** Reaktion auf Antippen. */
    interface Listener {

        void onClick(
                @NonNull CommitInfo commit
        );
    }

    private static final DiffUtil.ItemCallback<CommitInfo> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(
                @NonNull final CommitInfo oldItem,
                @NonNull final CommitInfo newItem
        ) {
            return oldItem.id().equals(newItem.id());
        }

        @Override
        public boolean areContentsTheSame(
                @NonNull final CommitInfo oldItem,
                @NonNull final CommitInfo newItem
        ) {
            return oldItem.equals(newItem);
        }
    };

    private final Listener listener;

    CommitAdapter(
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
        return new Holder(ItemCommitBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(
            @NonNull final Holder holder,
            final int position
    ) {
        holder.bind(getItem(position), listener);
    }

    /** Eine Commit-Zeile. */
    static final class Holder extends RecyclerView.ViewHolder {

        private final ItemCommitBinding binding;

        Holder(
                @NonNull final ItemCommitBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(
                @NonNull final CommitInfo commit,
                @NonNull final Listener listener
        ) {
            final Context context = binding.getRoot().getContext();
            binding.subject.setText(commit.subject().isEmpty() ? commit.shortId() : commit.subject());
            binding.meta.setText(metaOf(context, commit));
            final List<String> labels = new ArrayList<>();
            for (final RefLabel label : commit.refs()) {
                labels.add(label.kind() == RefLabel.Kind.TAG ? "⌂ " + label.name() : label.name());
            }
            binding.refs.setVisibility(labels.isEmpty() ? View.GONE : View.VISIBLE);
            binding.refs.setText(String.join("  ", labels));
            binding.getRoot().setContentDescription(commit.subject() + ", " + binding.meta.getText());
            binding.getRoot().setOnClickListener(view -> listener.onClick(commit));
        }

        private static String metaOf(
                final Context context,
                final CommitInfo commit
        ) {
            final StringBuilder meta = new StringBuilder(commit.authorName())
                    .append(" · ").append(RelativeTime.format(context, commit.timeMillis(), System.currentTimeMillis()))
                    .append(" · ").append(commit.shortId());
            if (commit.isMerge()) {
                meta.append(" · ").append(context.getString(R.string.history_merge));
            }
            return meta.toString();
        }
    }
}
