package de.lembergmax.gitmax.ui.activity;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.ItemOperationBinding;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.git.GitProgress;
import de.lembergmax.gitmax.ops.OperationEntry;
import de.lembergmax.gitmax.ops.OperationState;
import de.lembergmax.gitmax.ops.OperationTexts;
import de.lembergmax.gitmax.ui.common.PebbleView;
import de.lembergmax.gitmax.ui.common.RelativeTime;

import java.util.Objects;

/** Zeigt Vorgänge mit Kieselstein als Fortschritt und Ergebnis, Überschriften trennen laufend und beendet. */
final class OperationAdapter extends ListAdapter<ActivityViewModel.Item, RecyclerView.ViewHolder> {

    /** Reaktionen auf Antippen und Schaltflächen einer Zeile. */
    interface Listener {

        void onOpen(
                @NonNull OperationEntry entry,
                boolean retryable
        );

        void onCancel(
                @NonNull OperationEntry entry
        );

        void onRetry(
                @NonNull OperationEntry entry
        );
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_OPERATION = 1;
    private static final float DIMMED_ALPHA = 0.55f;

    private static final DiffUtil.ItemCallback<ActivityViewModel.Item> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(
                @NonNull final ActivityViewModel.Item oldItem,
                @NonNull final ActivityViewModel.Item newItem
        ) {
            if (oldItem.isHeader() || newItem.isHeader()) {
                return oldItem.isHeader() && newItem.isHeader() && oldItem.headerTitle() == newItem.headerTitle();
            }
            return oldItem.entry().id() == newItem.entry().id();
        }

        @Override
        public boolean areContentsTheSame(
                @NonNull final ActivityViewModel.Item oldItem,
                @NonNull final ActivityViewModel.Item newItem
        ) {
            return oldItem.equals(newItem);
        }
    };

    private final Listener listener;

    OperationAdapter(
            @NonNull final Listener listener
    ) {
        super(DIFF);
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public int getItemViewType(
            final int position
    ) {
        return getItem(position).isHeader() ? TYPE_HEADER : TYPE_OPERATION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull final ViewGroup parent,
            final int viewType
    ) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder((TextView) inflater.inflate(R.layout.item_section_header, parent, false));
        }
        return new OperationHolder(ItemOperationBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(
            @NonNull final RecyclerView.ViewHolder holder,
            final int position
    ) {
        final ActivityViewModel.Item item = getItem(position);
        if (holder instanceof HeaderHolder header) {
            header.title.setText(item.headerTitle());
        } else if (holder instanceof OperationHolder operation) {
            operation.bind(item, listener);
        }
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {

        private final TextView title;

        private HeaderHolder(
                @NonNull final TextView title
        ) {
            super(title);
            this.title = title;
        }
    }

    private static final class OperationHolder extends RecyclerView.ViewHolder {

        private final ItemOperationBinding binding;

        private OperationHolder(
                @NonNull final ItemOperationBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(
                @NonNull final ActivityViewModel.Item item,
                @NonNull final Listener listener
        ) {
            final OperationEntry entry = Objects.requireNonNull(item.entry());
            final Context context = binding.getRoot().getContext();
            final String status = statusLine(context, entry);

            binding.title.setText(entry.title());
            binding.status.setText(status);
            bindPebble(entry);
            bindAction(context, entry, item.retryable(), listener);

            binding.getRoot().setContentDescription(context.getString(R.string.activity_row_description,
                    entry.title(), OperationTexts.kind(context, entry.kind()), status));
            if (entry.state().isFinished()) {
                binding.getRoot().setOnClickListener(view -> listener.onOpen(entry, item.retryable()));
                binding.getRoot().setClickable(true);
            } else {
                binding.getRoot().setOnClickListener(null);
                binding.getRoot().setClickable(false);
            }
        }

        private void bindPebble(
                final OperationEntry entry
        ) {
            final PebbleView pebble = binding.pebble;
            pebble.setAlpha(entry.state() == OperationState.QUEUED || entry.state() == OperationState.CANCELLED
                    ? DIMMED_ALPHA : 1f);
            switch (entry.state()) {
                case RUNNING:
                    pebble.setStatus(PebbleView.Status.RUNNING);
                    final OperationEntry.Progress progress = entry.progress();
                    if (progress == null || progress.fraction() == GitProgress.UNKNOWN) {
                        pebble.setIndeterminate(true);
                    } else {
                        pebble.setProgress(progress.fraction());
                    }
                    break;
                case SUCCEEDED:
                    pebble.setStatus(PebbleView.Status.DONE);
                    break;
                case FAILED:
                    pebble.setStatus(entry.failure() != null && entry.failure().kind() == GitFailureKind.CONFLICT
                            ? PebbleView.Status.CONFLICT
                            : PebbleView.Status.FAILED);
                    break;
                case QUEUED:
                case CANCELLED:
                default:
                    pebble.setStatus(PebbleView.Status.CLEAN);
                    break;
            }
        }

        private void bindAction(
                final Context context,
                final OperationEntry entry,
                final boolean retryable,
                final Listener listener
        ) {
            if (!entry.state().isFinished()) {
                showAction(R.drawable.ic_close, context.getString(R.string.activity_cancel), () -> listener.onCancel(entry));
            } else if (retryable) {
                showAction(R.drawable.ic_refresh, context.getString(R.string.activity_retry), () -> listener.onRetry(entry));
            } else {
                binding.action.setVisibility(View.GONE);
            }
        }

        private void showAction(
                final int icon,
                final String description,
                final Runnable action
        ) {
            binding.action.setImageResource(icon);
            binding.action.setContentDescription(description);
            binding.action.setOnClickListener(view -> action.run());
            binding.action.setVisibility(View.VISIBLE);
        }

        private static String statusLine(
                final Context context,
                final OperationEntry entry
        ) {
            final String status = OperationTexts.status(context, entry);
            if (!entry.state().isFinished() || entry.finishedAt() == 0L) {
                return status;
            }
            final String time = RelativeTime.format(context, entry.finishedAt(), System.currentTimeMillis());
            return context.getString(R.string.activity_status_with_time, status, time);
        }
    }
}
