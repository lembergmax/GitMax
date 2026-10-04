package de.lembergmax.gitmax.ui.advanced;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.ItemDiffLineBinding;
import de.lembergmax.gitmax.databinding.ItemDiffSideBinding;
import de.lembergmax.gitmax.domain.model.DiffHunk;
import de.lembergmax.gitmax.domain.model.DiffLine;
import de.lembergmax.gitmax.domain.model.FileDiff;

import java.util.ArrayList;
import java.util.List;

/** Zeigt einen Datei-Diff untereinander (Unified) oder auf breiten Fenstern nebeneinander. */
final class DiffAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HUNK = 0;
    private static final int TYPE_LINE = 1;
    private static final int TYPE_SIDE = 2;

    /** Eine Zeile der Anzeige. */
    private record Row(
            @Nullable String hunk,
            @Nullable DiffLine line,
            @Nullable DiffLine left,
            @Nullable DiffLine right
    ) {
    }

    private List<Row> rows = List.of();

    void submit(
            @NonNull final FileDiff diff,
            final boolean sideBySide
    ) {
        final List<Row> built = new ArrayList<>();
        for (final DiffHunk hunk : diff.hunks()) {
            built.add(new Row(hunk.header(), null, null, null));
            if (sideBySide) {
                pair(hunk.lines(), built);
            } else {
                for (final DiffLine line : hunk.lines()) {
                    built.add(new Row(null, line, null, null));
                }
            }
        }
        rows = built;
        notifyDataSetChanged();
    }

    /** Stellt zusammenhängende entfernte und hinzugefügte Zeilen paarweise gegenüber. */
    private static void pair(
            final List<DiffLine> lines,
            final List<Row> out
    ) {
        int index = 0;
        while (index < lines.size()) {
            final DiffLine line = lines.get(index);
            if (line.type() == DiffLine.Type.CONTEXT) {
                out.add(new Row(null, null, line, line));
                index += 1;
                continue;
            }
            final List<DiffLine> removed = new ArrayList<>();
            final List<DiffLine> added = new ArrayList<>();
            while (index < lines.size() && lines.get(index).type() == DiffLine.Type.REMOVED) {
                removed.add(lines.get(index));
                index += 1;
            }
            while (index < lines.size() && lines.get(index).type() == DiffLine.Type.ADDED) {
                added.add(lines.get(index));
                index += 1;
            }
            final int count = Math.max(removed.size(), added.size());
            for (int pair = 0; pair < count; pair += 1) {
                out.add(new Row(null, null, pair < removed.size() ? removed.get(pair) : null,
                        pair < added.size() ? added.get(pair) : null));
            }
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public int getItemViewType(
            final int position
    ) {
        final Row row = rows.get(position);
        if (row.hunk() != null) {
            return TYPE_HUNK;
        }
        return row.line() != null ? TYPE_LINE : TYPE_SIDE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull final ViewGroup parent,
            final int viewType
    ) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_HUNK:
                return new HunkHolder((TextView) inflater.inflate(R.layout.item_diff_hunk, parent, false));
            case TYPE_LINE:
                return new LineHolder(ItemDiffLineBinding.inflate(inflater, parent, false));
            case TYPE_SIDE:
            default:
                return new SideHolder(ItemDiffSideBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(
            @NonNull final RecyclerView.ViewHolder holder,
            final int position
    ) {
        final Row row = rows.get(position);
        if (holder instanceof HunkHolder hunk) {
            hunk.text.setText(row.hunk());
        } else if (holder instanceof LineHolder line) {
            line.bind(row.line());
        } else if (holder instanceof SideHolder side) {
            side.bind(row.left(), row.right());
        }
    }

    private static int background(
            final View view,
            @Nullable final DiffLine line
    ) {
        if (line == null) {
            return Color.TRANSPARENT;
        }
        switch (line.type()) {
            case ADDED:
                return MaterialColors.getColor(view, R.attr.colorDiffAddedBackground);
            case REMOVED:
                return MaterialColors.getColor(view, R.attr.colorDiffRemovedBackground);
            case CONTEXT:
            default:
                return Color.TRANSPARENT;
        }
    }

    private static String sign(
            final DiffLine line
    ) {
        switch (line.type()) {
            case ADDED:
                return "+";
            case REMOVED:
                return "−";
            case CONTEXT:
            default:
                return "";
        }
    }

    private static String number(
            final int value
    ) {
        return value > 0 ? String.valueOf(value) : "";
    }

    private static final class HunkHolder extends RecyclerView.ViewHolder {

        private final TextView text;

        private HunkHolder(
                @NonNull final TextView text
        ) {
            super(text);
            this.text = text;
        }
    }

    private static final class LineHolder extends RecyclerView.ViewHolder {

        private final ItemDiffLineBinding binding;

        private LineHolder(
                @NonNull final ItemDiffLineBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(
                final DiffLine line
        ) {
            binding.oldNumber.setText(number(line.oldNumber()));
            binding.newNumber.setText(number(line.newNumber()));
            binding.sign.setText(sign(line));
            binding.text.setText(line.text());
            binding.getRoot().setBackgroundColor(background(binding.getRoot(), line));
            binding.getRoot().setContentDescription(describe(binding.getRoot(), line));
        }

        private static String describe(
                final View view,
                final DiffLine line
        ) {
            final int prefix = line.type() == DiffLine.Type.ADDED ? R.string.diff_added
                    : line.type() == DiffLine.Type.REMOVED ? R.string.diff_removed : R.string.diff_unchanged;
            return view.getContext().getString(prefix) + ": " + line.text();
        }
    }

    private static final class SideHolder extends RecyclerView.ViewHolder {

        private final ItemDiffSideBinding binding;

        private SideHolder(
                @NonNull final ItemDiffSideBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(
                @Nullable final DiffLine left,
                @Nullable final DiffLine right
        ) {
            binding.leftNumber.setText(left == null ? "" : number(left.oldNumber()));
            binding.leftText.setText(left == null ? "" : left.text());
            binding.left.setBackgroundColor(background(binding.getRoot(), left));
            binding.rightNumber.setText(right == null ? "" : number(right.newNumber()));
            binding.rightText.setText(right == null ? "" : right.text());
            binding.right.setBackgroundColor(background(binding.getRoot(), right));
        }
    }
}
