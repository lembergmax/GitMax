package de.lembergmax.gitmax.ui.advanced;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.lembergmax.gitmax.databinding.ItemSimpleRowBinding;

import java.util.Objects;

/** Zeigt {@link SimpleRow}s; Überschriften der Gruppen erscheinen vor der ersten Zeile jeder Gruppe. */
public final class SimpleRowAdapter extends ListAdapter<SimpleRow, SimpleRowAdapter.Holder> {

    /** Reaktionen auf Antippen. */
    public interface Listener {

        void onClick(
                @NonNull View anchor,
                @NonNull SimpleRow row
        );
    }

    private static final DiffUtil.ItemCallback<SimpleRow> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(
                @NonNull final SimpleRow oldItem,
                @NonNull final SimpleRow newItem
        ) {
            return oldItem.id().equals(newItem.id());
        }

        @Override
        public boolean areContentsTheSame(
                @NonNull final SimpleRow oldItem,
                @NonNull final SimpleRow newItem
        ) {
            return oldItem.equals(newItem);
        }
    };

    private final Listener listener;
    private final boolean withMenuButton;

    /**
     * @param listener       Reaktion auf Antippen
     * @param withMenuButton {@code true}: Drei-Punkte-Schaltfläche, {@code false}: Pfeil nach rechts
     */
    public SimpleRowAdapter(
            @NonNull final Listener listener,
            final boolean withMenuButton
    ) {
        super(DIFF);
        this.listener = Objects.requireNonNull(listener, "listener");
        this.withMenuButton = withMenuButton;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(
            @NonNull final ViewGroup parent,
            final int viewType
    ) {
        return new Holder(ItemSimpleRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(
            @NonNull final Holder holder,
            final int position
    ) {
        final SimpleRow row = getItem(position);
        final SimpleRow previous = position == 0 ? null : getItem(position - 1);
        final boolean newSection = row.section() != null && (previous == null || !row.section().equals(previous.section()));
        holder.bind(row, newSection, withMenuButton, listener);
    }

    /** Eine Zeile. */
    public static final class Holder extends RecyclerView.ViewHolder {

        private final ItemSimpleRowBinding binding;
        // setTypeface(tf, NORMAL) setzt nur tf: ginge man vom zuletzt gesetzten (fetten) Typeface aus, bliebe jede recycelte Zeile fett.
        private final Typeface titleTypeface;

        Holder(
                @NonNull final ItemSimpleRowBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
            this.titleTypeface = binding.title.getTypeface();
        }

        void bind(
                @NonNull final SimpleRow row,
                final boolean showSection,
                final boolean withMenuButton,
                @NonNull final Listener listener
        ) {
            binding.section.setVisibility(showSection ? View.VISIBLE : View.GONE);
            binding.section.setText(row.section());
            binding.title.setText(row.title());
            binding.title.setTypeface(titleTypeface, row.emphasized() ? Typeface.BOLD : Typeface.NORMAL);
            binding.subtitle.setText(row.subtitle());
            binding.subtitle.setVisibility(row.subtitle().isEmpty() ? View.GONE : View.VISIBLE);
            binding.icon.setImageResource(row.icon());
            binding.more.setVisibility(withMenuButton ? View.VISIBLE : View.GONE);
            binding.chevron.setVisibility(withMenuButton ? View.GONE : View.VISIBLE);
            binding.row.setOnClickListener(view -> listener.onClick(binding.more.getVisibility() == View.VISIBLE ? binding.more : view, row));
            binding.more.setOnClickListener(view -> listener.onClick(view, row));
            binding.row.setContentDescription(row.subtitle().isEmpty() ? row.title() : row.title() + ", " + row.subtitle());
        }
    }
}
