package de.lembergmax.gitmax.ui.advanced;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.ItemCommitFileBinding;
import de.lembergmax.gitmax.databinding.ItemCommitHeaderBinding;
import de.lembergmax.gitmax.domain.model.CommitDetail;
import de.lembergmax.gitmax.domain.model.CommitInfo;
import de.lembergmax.gitmax.domain.model.FileChange;
import de.lembergmax.gitmax.domain.model.RefLabel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/** Zeigt einen Commit: Kopf mit Nachricht und Angaben, darunter die geänderten Dateien. */
final class CommitDetailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** Reaktion auf Antippen einer Datei. */
    interface Listener {

        void onFileClick(
                @NonNull FileChange file
        );
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_FILE = 1;

    private final Listener listener;
    private CommitDetail detail;

    CommitDetailAdapter(
            @NonNull final Listener listener
    ) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    void submit(
            @NonNull final CommitDetail newDetail
    ) {
        detail = newDetail;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return detail == null ? 0 : detail.files().size() + 1;
    }

    @Override
    public int getItemViewType(
            final int position
    ) {
        return position == 0 ? TYPE_HEADER : TYPE_FILE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull final ViewGroup parent,
            final int viewType
    ) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(ItemCommitHeaderBinding.inflate(inflater, parent, false));
        }
        return new FileHolder(ItemCommitFileBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(
            @NonNull final RecyclerView.ViewHolder holder,
            final int position
    ) {
        if (holder instanceof HeaderHolder header) {
            header.bind(detail);
        } else if (holder instanceof FileHolder file) {
            file.bind(detail.files().get(position - 1), listener);
        }
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {

        private final ItemCommitHeaderBinding binding;

        private HeaderHolder(
                @NonNull final ItemCommitHeaderBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(
                final CommitDetail detail
        ) {
            final Context context = binding.getRoot().getContext();
            final CommitInfo info = detail.info();
            final String[] parts = info.message().split("\n", 2);
            binding.subject.setText(info.subject().isEmpty() ? info.shortId() : parts[0]);
            final String rest = parts.length > 1 ? parts[1].strip() : "";
            binding.body.setVisibility(rest.isEmpty() ? View.GONE : View.VISIBLE);
            binding.body.setText(rest);
            binding.author.setText(context.getString(R.string.commit_author_line, info.authorName(), info.authorEmail(),
                    DateFormat.getMediumDateFormat(context).format(new Date(info.timeMillis()))
                            + " " + DateFormat.getTimeFormat(context).format(new Date(info.timeMillis()))));
            final StringBuilder id = new StringBuilder(info.id());
            if (!info.parents().isEmpty()) {
                final List<String> shortParents = new ArrayList<>();
                for (final String parent : info.parents()) {
                    shortParents.add(parent.substring(0, CommitInfo.SHORT_ID_LENGTH));
                }
                id.append('\n').append(context.getString(R.string.commit_parents, String.join(", ", shortParents)));
            }
            binding.commitId.setText(id);
            final List<String> labels = new ArrayList<>();
            for (final RefLabel label : info.refs()) {
                labels.add(label.name());
            }
            binding.refs.setVisibility(labels.isEmpty() ? View.GONE : View.VISIBLE);
            binding.refs.setText(String.join("  ", labels));
            binding.filesTitle.setText(context.getResources().getQuantityString(
                    R.plurals.commit_files_title, detail.files().size(), detail.files().size()));
        }
    }

    private static final class FileHolder extends RecyclerView.ViewHolder {

        private final ItemCommitFileBinding binding;

        private FileHolder(
                @NonNull final ItemCommitFileBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(
                final FileChange file,
                final Listener listener
        ) {
            final Context context = binding.getRoot().getContext();
            final String path = file.path();
            final int slash = path.lastIndexOf('/');
            binding.name.setText(slash < 0 ? path : path.substring(slash + 1));
            binding.folder.setText(slash < 0 ? "" : path.substring(0, slash));
            binding.folder.setVisibility(slash < 0 ? View.GONE : View.VISIBLE);
            binding.kind.setImageResource(iconOf(file.kind()));
            binding.kind.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(binding.kind, colorOf(file.kind()))));
            binding.counts.setText(file.binary() ? context.getString(R.string.commit_file_binary) : "+" + file.added() + " −" + file.removed());
            binding.getRoot().setContentDescription(path + ", " + binding.counts.getText());
            binding.getRoot().setOnClickListener(view -> listener.onFileClick(file));
        }

        @DrawableRes
        private static int iconOf(
                final FileChange.Kind kind
        ) {
            switch (kind) {
                case ADDED:
                    return R.drawable.ic_add;
                case DELETED:
                    return R.drawable.ic_delete;
                case RENAMED:
                    return R.drawable.ic_drive_file_rename_outline;
                case MODIFIED:
                default:
                    return R.drawable.ic_edit;
            }
        }

        @AttrRes
        private static int colorOf(
                final FileChange.Kind kind
        ) {
            switch (kind) {
                case ADDED:
                    return R.attr.colorGitAdded;
                case DELETED:
                    return androidx.appcompat.R.attr.colorError;
                case RENAMED:
                    return androidx.appcompat.R.attr.colorPrimary;
                case MODIFIED:
                default:
                    return R.attr.colorGitModified;
            }
        }
    }
}
