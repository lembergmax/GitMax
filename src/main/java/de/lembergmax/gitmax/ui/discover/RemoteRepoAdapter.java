package de.lembergmax.gitmax.ui.discover;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.ItemRemoteRepoBinding;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.domain.model.RemoteRepo;
import de.lembergmax.gitmax.ui.common.Motion;
import de.lembergmax.gitmax.ui.common.RelativeTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Zeigt die Repos der Anbieter. Die Kachel trägt den Anbieter; ausgewählte Zeilen zeigen stattdessen
 * ein Häkchen auf der Primärfarbe.
 */
final class RemoteRepoAdapter extends ListAdapter<DiscoverViewModel.Row, RemoteRepoAdapter.Holder> {

    /** Reaktionen auf Antippen und langes Drücken einer Zeile. */
    interface Listener {

        void onClick(
                @NonNull DiscoverViewModel.Row row
        );

        void onLongClick(
                @NonNull DiscoverViewModel.Row row
        );
    }

    private static final Object PAYLOAD_SELECTION = new Object();

    private static final DiffUtil.ItemCallback<DiscoverViewModel.Row> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(
                @NonNull final DiscoverViewModel.Row oldItem,
                @NonNull final DiscoverViewModel.Row newItem
        ) {
            return oldItem.key().equals(newItem.key());
        }

        @Override
        public boolean areContentsTheSame(
                @NonNull final DiscoverViewModel.Row oldItem,
                @NonNull final DiscoverViewModel.Row newItem
        ) {
            return oldItem.equals(newItem);
        }

        @Override
        public Object getChangePayload(
                @NonNull final DiscoverViewModel.Row oldItem,
                @NonNull final DiscoverViewModel.Row newItem
        ) {
            final boolean onlySelectionChanged = oldItem.repo().equals(newItem.repo())
                    && oldItem.cloned() == newItem.cloned()
                    && oldItem.selected() != newItem.selected();
            return onlySelectionChanged ? PAYLOAD_SELECTION : null;
        }
    };

    private final Listener listener;

    RemoteRepoAdapter(
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
        return new Holder(ItemRemoteRepoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
            holder.updateDescription(getItem(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    /** Eine Repo-Zeile. */
    static final class Holder extends RecyclerView.ViewHolder {

        private final ItemRemoteRepoBinding binding;

        Holder(
                @NonNull final ItemRemoteRepoBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(
                @NonNull final DiscoverViewModel.Row row,
                @NonNull final Listener listener,
                final boolean animate
        ) {
            final RemoteRepo repo = row.repo();
            final Context context = binding.getRoot().getContext();
            binding.tile.setText(row.provider() == ProviderType.GITLAB ? "GL" : "GH");
            binding.name.setText(repo.name());
            binding.subtitle.setText(subtitle(repo));
            binding.facts.setText(facts(context, row));
            showSelection(row, animate);
            updateDescription(row);
            binding.getRoot().setOnClickListener(view -> listener.onClick(row));
            binding.getRoot().setOnLongClickListener(view -> {
                listener.onLongClick(row);
                return true;
            });
        }

        void updateDescription(
                @NonNull final DiscoverViewModel.Row row
        ) {
            final Context context = binding.getRoot().getContext();
            binding.getRoot().setContentDescription(row.repo().fullPath() + ". " + binding.facts.getText());
            ViewCompat.setStateDescription(binding.getRoot(), context.getString(
                    row.selected() ? R.string.discover_row_selected : R.string.discover_row_not_selected));
        }

        void showSelection(
                @NonNull final DiscoverViewModel.Row row,
                final boolean animate
        ) {
            Motion.swapIcon(binding.check, row.selected(), animate);
        }

        private static String subtitle(
                final RemoteRepo repo
        ) {
            final String owner = repo.owner();
            if (repo.description().isEmpty()) {
                return owner;
            }
            return owner.isEmpty() ? repo.description() : owner + " · " + repo.description();
        }

        private static CharSequence facts(
                final Context context,
                final DiscoverViewModel.Row row
        ) {
            final RemoteRepo repo = row.repo();
            final List<CharSequence> parts = new ArrayList<>();
            parts.add(context.getString(repo.isPrivate() ? R.string.discover_fact_private : R.string.discover_fact_public));
            if (repo.isFork()) {
                parts.add(context.getString(R.string.discover_fact_fork));
            }
            if (repo.isArchived()) {
                parts.add(context.getString(R.string.discover_fact_archived));
            }
            if (row.cloned()) {
                final SpannableStringBuilder cloned = new SpannableStringBuilder(context.getString(R.string.discover_fact_cloned));
                cloned.setSpan(new StyleSpan(Typeface.BOLD), 0, cloned.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                cloned.setSpan(new ForegroundColorSpan(MaterialColors.getColor(context,
                        androidx.appcompat.R.attr.colorPrimary, 0)), 0, cloned.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                parts.add(cloned);
            }
            if (repo.lastActivityMillis() > 0L) {
                parts.add(context.getString(R.string.discover_fact_activity,
                        RelativeTime.format(context, repo.lastActivityMillis(), System.currentTimeMillis())));
            }
            final SpannableStringBuilder joined = new SpannableStringBuilder();
            for (final CharSequence part : parts) {
                if (joined.length() > 0) {
                    joined.append(" · ");
                }
                joined.append(part);
            }
            return joined;
        }
    }
}
