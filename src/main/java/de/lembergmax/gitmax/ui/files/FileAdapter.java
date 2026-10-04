package de.lembergmax.gitmax.ui.files;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.ItemFileBinding;
import de.lembergmax.gitmax.domain.model.ChangedFile;
import de.lembergmax.gitmax.storage.RepoFiles;

import java.util.Objects;

/** Zeigt die Einträge eines Ordners; geänderte Dateien und Ordner mit Änderungen sind markiert. */
final class FileAdapter extends ListAdapter<FilesViewModel.Row, FileAdapter.Holder> {

    /** Reaktionen auf Antippen und das Menü einer Zeile. */
    interface Listener {

        void onOpen(
                @NonNull RepoFiles.Entry entry
        );

        void onMore(
                @NonNull View anchor,
                @NonNull RepoFiles.Entry entry
        );
    }

    private static final DiffUtil.ItemCallback<FilesViewModel.Row> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(
                @NonNull final FilesViewModel.Row oldItem,
                @NonNull final FilesViewModel.Row newItem
        ) {
            return oldItem.entry().path().equals(newItem.entry().path());
        }

        @Override
        public boolean areContentsTheSame(
                @NonNull final FilesViewModel.Row oldItem,
                @NonNull final FilesViewModel.Row newItem
        ) {
            return oldItem.equals(newItem);
        }
    };

    private final Listener listener;

    FileAdapter(
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
        return new Holder(ItemFileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(
            @NonNull final Holder holder,
            final int position
    ) {
        holder.bind(getItem(position), listener);
    }

    /** Eine Zeile. */
    static final class Holder extends RecyclerView.ViewHolder {

        private final ItemFileBinding binding;

        Holder(
                @NonNull final ItemFileBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(
                @NonNull final FilesViewModel.Row row,
                @NonNull final Listener listener
        ) {
            final Context context = binding.getRoot().getContext();
            final RepoFiles.Entry entry = row.entry();
            final String detail = detailOf(context, row);

            binding.name.setText(entry.name());
            binding.detail.setText(detail);
            binding.icon.setImageResource(entry.directory() ? R.drawable.ic_folder : R.drawable.ic_description);
            binding.icon.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(binding.icon, tintFor(row))));
            binding.getRoot().setContentDescription(context.getString(R.string.files_row_description, entry.name(), detail));
            binding.getRoot().setOnClickListener(view -> listener.onOpen(entry));
            binding.more.setOnClickListener(view -> listener.onMore(view, entry));
            binding.getRoot().setOnLongClickListener(view -> {
                listener.onMore(binding.more, entry);
                return true;
            });
        }

        @AttrRes
        private static int tintFor(
                final FilesViewModel.Row row
        ) {
            if (row.entry().directory()) {
                return row.hasChanges() ? R.attr.colorGitModified : androidx.appcompat.R.attr.colorPrimary;
            }
            if (row.kind() == null) {
                return com.google.android.material.R.attr.colorOnSurfaceVariant;
            }
            switch (row.kind()) {
                case ADDED:
                    return R.attr.colorGitAdded;
                case DELETED:
                    return androidx.appcompat.R.attr.colorError;
                case CONFLICT:
                    return R.attr.colorGitConflict;
                case UNTRACKED:
                    return androidx.appcompat.R.attr.colorPrimary;
                case MODIFIED:
                default:
                    return R.attr.colorGitModified;
            }
        }

        private static String detailOf(
                final Context context,
                final FilesViewModel.Row row
        ) {
            final RepoFiles.Entry entry = row.entry();
            if (entry.directory()) {
                final String folder = context.getString(R.string.files_detail_folder);
                return row.hasChanges()
                        ? context.getString(R.string.files_detail_with_kind, folder, context.getString(R.string.files_folder_has_changes))
                        : folder;
            }
            final String size = Formatter.formatShortFileSize(context, entry.size());
            final ChangedFile.Kind kind = row.kind();
            return kind == null ? size : context.getString(R.string.files_detail_with_kind, size, context.getString(kindText(kind)));
        }

        private static int kindText(
                final ChangedFile.Kind kind
        ) {
            switch (kind) {
                case ADDED:
                    return R.string.repo_kind_added;
                case DELETED:
                    return R.string.repo_kind_deleted;
                case UNTRACKED:
                    return R.string.repo_kind_untracked;
                case CONFLICT:
                    return R.string.repo_kind_conflict;
                case MODIFIED:
                default:
                    return R.string.repo_kind_modified;
            }
        }
    }
}
