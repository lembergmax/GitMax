package de.lembergmax.gitmax.ui.picker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.ItemFolderBinding;

import java.util.Objects;

/**
 * Zeigt die Einträge der Ordnerauswahl: Speicher, „Eine Ebene höher“ und Unterordner.
 */
final class FolderAdapter extends ListAdapter<FolderPickerViewModel.Entry, FolderAdapter.Holder> {

    /** Wird beim Antippen eines Eintrags aufgerufen. */
    interface OnEntryClick {

        void onEntryClick(
                @NonNull FolderPickerViewModel.Entry entry
        );
    }

    private static final DiffUtil.ItemCallback<FolderPickerViewModel.Entry> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(
                @NonNull final FolderPickerViewModel.Entry oldItem,
                @NonNull final FolderPickerViewModel.Entry newItem
        ) {
            return oldItem.type() == newItem.type() && Objects.equals(oldItem.file(), newItem.file());
        }

        @Override
        public boolean areContentsTheSame(
                @NonNull final FolderPickerViewModel.Entry oldItem,
                @NonNull final FolderPickerViewModel.Entry newItem
        ) {
            return oldItem.equals(newItem);
        }
    };

    private final OnEntryClick listener;

    FolderAdapter(
            @NonNull final OnEntryClick listener
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
        return new Holder(ItemFolderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(
            @NonNull final Holder holder,
            final int position
    ) {
        holder.bind(getItem(position), listener);
    }

    /** Eine Zeile der Ordnerauswahl. */
    static final class Holder extends RecyclerView.ViewHolder {

        private final ItemFolderBinding binding;

        Holder(
                @NonNull final ItemFolderBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(
                @NonNull final FolderPickerViewModel.Entry entry,
                @NonNull final OnEntryClick listener
        ) {
            binding.name.setText(entry.label());
            binding.icon.setImageResource(iconFor(entry.type()));
            binding.repoMarker.setVisibility(entry.isRepo() ? View.VISIBLE : View.GONE);
            binding.getRoot().setOnClickListener(view -> listener.onEntryClick(entry));
        }

        private static int iconFor(
                final FolderPickerViewModel.Type type
        ) {
            switch (type) {
                case UP:
                    return R.drawable.ic_arrow_upward;
                case VOLUME:
                    return R.drawable.ic_folder_open;
                case FOLDER:
                default:
                    return R.drawable.ic_folder;
            }
        }
    }
}
