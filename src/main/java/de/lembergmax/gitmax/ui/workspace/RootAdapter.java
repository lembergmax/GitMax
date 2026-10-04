package de.lembergmax.gitmax.ui.workspace;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.ItemRootBinding;

import java.io.File;
import java.util.Objects;

/**
 * Zeigt die Arbeitsordner; der Standard-Zielordner trägt eine Marke, der Überlauf-Knopf öffnet das Menü.
 */
final class RootAdapter extends ListAdapter<RootAdapter.Item, RootAdapter.Holder> {

    /**
     * Ein Arbeitsordner mit Standard-Kennzeichen.
     *
     * @param folder    der Ordner
     * @param isDefault {@code true} beim Standard-Zielordner
     */
    record Item(
            @NonNull File folder,
            boolean isDefault
    ) {
    }

    /** Wird beim Antippen des Überlauf-Knopfs aufgerufen. */
    interface OnMoreClick {

        void onMoreClick(
                @NonNull View anchor,
                @NonNull Item item
        );
    }

    private static final DiffUtil.ItemCallback<Item> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(
                @NonNull final Item oldItem,
                @NonNull final Item newItem
        ) {
            return oldItem.folder().equals(newItem.folder());
        }

        @Override
        public boolean areContentsTheSame(
                @NonNull final Item oldItem,
                @NonNull final Item newItem
        ) {
            return oldItem.equals(newItem);
        }
    };

    private final OnMoreClick listener;

    RootAdapter(
            @NonNull final OnMoreClick listener
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
        return new Holder(ItemRootBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(
            @NonNull final Holder holder,
            final int position
    ) {
        holder.bind(getItem(position), listener);
    }

    /** Eine Zeile der Arbeitsordner. */
    static final class Holder extends RecyclerView.ViewHolder {

        private final ItemRootBinding binding;

        Holder(
                @NonNull final ItemRootBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(
                @NonNull final Item item,
                @NonNull final OnMoreClick listener
        ) {
            binding.name.setText(item.folder().getName());
            binding.path.setText(item.folder().getAbsolutePath());
            binding.defaultMarker.setVisibility(item.isDefault() ? View.VISIBLE : View.GONE);
            binding.more.setContentDescription(binding.getRoot().getContext()
                    .getString(R.string.workspace_row_more, item.folder().getName()));
            binding.more.setOnClickListener(view -> listener.onMoreClick(view, item));
        }
    }
}
