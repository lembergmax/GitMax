package de.lembergmax.gitmax.ui.viewer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.ItemBlameHeaderBinding;
import de.lembergmax.gitmax.databinding.ItemBlameLineBinding;
import de.lembergmax.gitmax.domain.model.BlameLine;
import de.lembergmax.gitmax.ui.common.RelativeTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Zeigt Blame: vor jeder zusammenhängenden Gruppe von Zeilen desselben Commits steht eine Kopfzeile mit Kennung, Autor,
 * Alter und Betreff; Gruppen wechseln sich in einem dezenten Hintergrund ab, damit man ihre Grenzen sieht.
 */
final class BlameAdapter extends ListAdapter<BlameAdapter.Item, RecyclerView.ViewHolder> {

    /** Eine Zeile der Liste: Kopf einer Gruppe oder eine Codezeile. */
    record Item(
            @NonNull String id,
            @Nullable BlameLine head,
            @Nullable BlameLine line,
            boolean shaded
    ) {

        boolean isHeader() {
            return head != null;
        }
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_LINE = 1;

    private static final DiffUtil.ItemCallback<Item> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(
                @NonNull final Item oldItem,
                @NonNull final Item newItem
        ) {
            return oldItem.id().equals(newItem.id());
        }

        @Override
        public boolean areContentsTheSame(
                @NonNull final Item oldItem,
                @NonNull final Item newItem
        ) {
            return oldItem.equals(newItem);
        }
    };

    private final Consumer<BlameLine> onCommit;

    BlameAdapter(
            @NonNull final Consumer<BlameLine> onCommit
    ) {
        super(DIFF);
        this.onCommit = Objects.requireNonNull(onCommit, "onCommit");
    }

    /** Baut aus den Zeilen die Liste mit den Kopfzeilen. */
    @NonNull
    static List<Item> itemsOf(
            @NonNull final List<BlameLine> lines
    ) {
        final List<Item> items = new ArrayList<>(lines.size() + lines.size() / 4);
        String previous = null;
        boolean shaded = true;
        for (final BlameLine line : lines) {
            if (previous == null || !previous.equals(line.commitId())) {
                shaded = !shaded;
                items.add(new Item("h:" + line.number(), line, null, shaded));
                previous = line.commitId();
            }
            items.add(new Item("l:" + line.number(), null, line, shaded));
        }
        return items;
    }

    @Override
    public int getItemViewType(
            final int position
    ) {
        return getItem(position).isHeader() ? TYPE_HEADER : TYPE_LINE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull final ViewGroup parent,
            final int viewType
    ) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return viewType == TYPE_HEADER
                ? new HeaderHolder(ItemBlameHeaderBinding.inflate(inflater, parent, false))
                : new LineHolder(ItemBlameLineBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(
            @NonNull final RecyclerView.ViewHolder holder,
            final int position
    ) {
        final Item item = getItem(position);
        if (holder instanceof HeaderHolder header) {
            header.bind(item, onCommit);
        } else if (holder instanceof LineHolder line) {
            line.bind(item);
        }
    }

    private static void shade(
            final View view,
            final boolean shaded
    ) {
        final Context context = view.getContext();
        if (shaded) {
            view.setBackgroundColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerLow, 0));
        } else {
            view.setBackgroundColor(0);
        }
    }

    /** Kopf einer Gruppe. */
    private static final class HeaderHolder extends RecyclerView.ViewHolder {

        private final ItemBlameHeaderBinding binding;

        private HeaderHolder(
                @NonNull final ItemBlameHeaderBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(
                final Item item,
                final Consumer<BlameLine> onCommit
        ) {
            final BlameLine line = Objects.requireNonNull(item.head());
            final Context context = binding.getRoot().getContext();
            shade(binding.getRoot(), item.shaded());
            if (line.isCommitted()) {
                binding.commit.setText(line.shortId());
                binding.who.setText(context.getString(R.string.blame_author_line, line.authorName(),
                        RelativeTime.format(context, line.timeMillis(), System.currentTimeMillis())));
                binding.subject.setText(line.subject());
                binding.subject.setVisibility(View.VISIBLE);
                binding.getRoot().setOnClickListener(view -> onCommit.accept(line));
                binding.getRoot().setClickable(true);
                binding.getRoot().setContentDescription(context.getString(R.string.blame_header_description,
                        line.shortId(), line.authorName(), line.subject()));
            } else {
                binding.commit.setText("");
                binding.who.setText(R.string.blame_not_committed);
                binding.subject.setVisibility(View.GONE);
                binding.getRoot().setOnClickListener(null);
                binding.getRoot().setClickable(false);
                binding.getRoot().setContentDescription(context.getString(R.string.blame_not_committed));
            }
        }
    }

    /** Eine Codezeile. */
    private static final class LineHolder extends RecyclerView.ViewHolder {

        private final ItemBlameLineBinding binding;

        private LineHolder(
                @NonNull final ItemBlameLineBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(
                final Item item
        ) {
            final BlameLine line = Objects.requireNonNull(item.line());
            shade(binding.getRoot(), item.shaded());
            binding.number.setText(String.valueOf(line.number()));
            binding.text.setText(line.text());
        }
    }
}
