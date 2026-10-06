package de.lembergmax.gitmax.ui.repo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.ItemChangeFileBinding;
import de.lembergmax.gitmax.databinding.ItemChangeSectionBinding;
import de.lembergmax.gitmax.databinding.ItemRepoHeaderBinding;
import de.lembergmax.gitmax.domain.model.ChangedFile;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.RepoStatus;
import de.lembergmax.gitmax.git.GitProgress;
import de.lembergmax.gitmax.ops.OperationEntry;
import de.lembergmax.gitmax.ops.OperationKind;
import de.lembergmax.gitmax.ops.OperationTexts;
import de.lembergmax.gitmax.ui.common.AdaptiveRow;
import de.lembergmax.gitmax.ui.common.PebbleView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Zeigt Kopf, Abschnitte und Dateien eines Repos in einer Liste. */
final class RepoDetailAdapter extends ListAdapter<RepoDetailAdapter.Item, RecyclerView.ViewHolder> {

    /** Abschnitte der Änderungsliste. */
    enum Section {
        CONFLICTS,
        STAGED,
        UNSTAGED,
        UNTRACKED
    }

    /** Eine Zeile der Liste. */
    interface Item {

        /** Stabile Kennung für den Listenvergleich. */
        @NonNull
        String id();

        /** Alle Zeilenarten sind Records; der Listenvergleich verlässt sich auf deren {@code equals}. */
        @Override
        boolean equals(Object other);

        @Override
        int hashCode();
    }

    /** Kopfzeile mit Zustand und Aktionen. */
    record HeaderItem(@NonNull RepoDetailViewModel.UiState state) implements Item {

        @NonNull
        @Override
        public String id() {
            return "header";
        }
    }

    /** Überschrift eines Abschnitts mit Sammelaktion. */
    record SectionItem(@NonNull Section section, @NonNull List<ChangedFile> files) implements Item {

        @NonNull
        @Override
        public String id() {
            return "section:" + section;
        }
    }

    /** Eine geänderte Datei. */
    record FileItem(@NonNull Section section, @NonNull ChangedFile file) implements Item {

        @NonNull
        @Override
        public String id() {
            return "file:" + section + ":" + file.path();
        }
    }

    /** Hinweis, dass nichts zu committen ist. */
    record CleanItem() implements Item {

        @NonNull
        @Override
        public String id() {
            return "clean";
        }
    }

    /** Reaktionen auf Bedienung. */
    interface Listener {

        void onUpdate();

        void onCommit();

        void onPush();

        void onFailureAction();

        void onFailureDismiss();

        void onToggle(
                @NonNull Section section,
                @NonNull ChangedFile file
        );

        void onFileMenu(
                @NonNull View anchor,
                @NonNull Section section,
                @NonNull ChangedFile file
        );

        void onSectionAction(
                @NonNull Section section,
                @NonNull List<ChangedFile> files
        );
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SECTION = 1;
    private static final int TYPE_FILE = 2;
    private static final int TYPE_CLEAN = 3;
    private static final int PERCENT = 100;
    // Material 3: 12 % für den Untergrund und 38 % für Text und Symbol eines nicht bedienbaren Knopfs.
    private static final int DISABLED_BACKGROUND_ALPHA = 31;
    private static final int DISABLED_CONTENT_ALPHA = 97;

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

    private final Listener listener;

    RepoDetailAdapter(
            @NonNull final Listener listener
    ) {
        super(DIFF);
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public int getItemViewType(
            final int position
    ) {
        final Item item = getItem(position);
        if (item instanceof HeaderItem) {
            return TYPE_HEADER;
        }
        if (item instanceof SectionItem) {
            return TYPE_SECTION;
        }
        return item instanceof FileItem ? TYPE_FILE : TYPE_CLEAN;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull final ViewGroup parent,
            final int viewType
    ) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_HEADER:
                return new HeaderHolder(ItemRepoHeaderBinding.inflate(inflater, parent, false));
            case TYPE_SECTION:
                return new SectionHolder(ItemChangeSectionBinding.inflate(inflater, parent, false));
            case TYPE_FILE:
                return new FileHolder(ItemChangeFileBinding.inflate(inflater, parent, false));
            case TYPE_CLEAN:
            default:
                return new CleanHolder(inflater.inflate(R.layout.item_repo_clean, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(
            @NonNull final RecyclerView.ViewHolder holder,
            final int position
    ) {
        final Item item = getItem(position);
        if (holder instanceof HeaderHolder header) {
            header.bind(((HeaderItem) item).state(), listener);
        } else if (holder instanceof SectionHolder section) {
            section.bind((SectionItem) item, listener);
        } else if (holder instanceof FileHolder file) {
            file.bind((FileItem) item, listener);
        }
    }

    /** Baut aus dem Zustand die Liste: Kopf, dann je nicht leerem Abschnitt Überschrift und Dateien. */
    static List<Item> itemsFor(
            @NonNull final RepoDetailViewModel.UiState state
    ) {
        final List<Item> items = new ArrayList<>();
        items.add(new HeaderItem(state));
        if (state.tree() == null) {
            return items;
        }
        addSection(items, Section.CONFLICTS, state.tree().conflicts());
        addSection(items, Section.STAGED, state.tree().staged());
        addSection(items, Section.UNSTAGED, state.tree().unstaged());
        addSection(items, Section.UNTRACKED, state.tree().untracked());
        if (state.tree().isClean() && !state.unreadable()) {
            items.add(new CleanItem());
        }
        return items;
    }

    private static void addSection(
            final List<Item> items,
            final Section section,
            final List<ChangedFile> files
    ) {
        if (files.isEmpty()) {
            return;
        }
        items.add(new SectionItem(section, files));
        for (final ChangedFile file : files) {
            items.add(new FileItem(section, file));
        }
    }

    /* ------------------------------------------------------------------------------------------ */

    private static final class CleanHolder extends RecyclerView.ViewHolder {

        private CleanHolder(
                @NonNull final View view
        ) {
            super(view);
        }
    }

    private static final class SectionHolder extends RecyclerView.ViewHolder {

        private final ItemChangeSectionBinding binding;

        private SectionHolder(
                @NonNull final ItemChangeSectionBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(
                final SectionItem item,
                final Listener listener
        ) {
            final Context context = binding.getRoot().getContext();
            final int title;
            switch (item.section()) {
                case CONFLICTS:
                    title = R.string.repo_section_conflicts;
                    break;
                case STAGED:
                    title = R.string.repo_section_staged;
                    break;
                case UNSTAGED:
                    title = R.string.repo_section_unstaged;
                    break;
                case UNTRACKED:
                default:
                    title = R.string.repo_section_untracked;
                    break;
            }
            binding.title.setText(context.getString(R.string.repo_section_title, context.getString(title), item.files().size()));
            binding.action.setVisibility(View.VISIBLE);
            binding.action.setText(item.section() == Section.CONFLICTS ? R.string.conflicts_open
                    : item.section() == Section.STAGED ? R.string.repo_unstage_all : R.string.repo_stage_all);
            binding.action.setOnClickListener(button -> listener.onSectionAction(item.section(), item.files()));
        }
    }

    private static final class FileHolder extends RecyclerView.ViewHolder {

        private final ItemChangeFileBinding binding;

        private FileHolder(
                @NonNull final ItemChangeFileBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(
                final FileItem item,
                final Listener listener
        ) {
            final Context context = binding.getRoot().getContext();
            final ChangedFile file = item.file();
            final String path = file.path();
            final int slash = path.lastIndexOf('/');
            final String kindLabel = context.getString(kindText(file.kind()));

            binding.name.setText(slash < 0 ? path : path.substring(slash + 1));
            binding.detail.setText(slash < 0 ? kindLabel : kindLabel + " · " + path.substring(0, slash));
            binding.kind.setImageResource(kindIcon(file.kind()));
            binding.kind.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(binding.kind, kindColor(file.kind()))));
            binding.getRoot().setContentDescription(context.getString(R.string.repo_file_description, path, kindLabel));

            final boolean conflict = item.section() == Section.CONFLICTS;
            if (conflict) {
                binding.toggle.setVisibility(View.GONE);
                binding.getRoot().setOnClickListener(null);
                binding.getRoot().setClickable(false);
            } else {
                final boolean staged = item.section() == Section.STAGED;
                binding.toggle.setVisibility(View.VISIBLE);
                binding.toggle.setImageResource(staged ? R.drawable.ic_remove : R.drawable.ic_add_circle);
                binding.toggle.setContentDescription(context.getString(staged ? R.string.repo_unstage : R.string.repo_stage));
                binding.toggle.setOnClickListener(button -> listener.onToggle(item.section(), file));
                binding.getRoot().setOnClickListener(view -> listener.onToggle(item.section(), file));
            }
            binding.getRoot().setOnLongClickListener(view -> {
                listener.onFileMenu(view, item.section(), file);
                return true;
            });
        }

        @DrawableRes
        private static int kindIcon(
                final ChangedFile.Kind kind
        ) {
            switch (kind) {
                case ADDED:
                    return R.drawable.ic_add;
                case DELETED:
                    return R.drawable.ic_delete;
                case UNTRACKED:
                    return R.drawable.ic_note_add;
                case CONFLICT:
                    return R.drawable.ic_call_merge;
                case MODIFIED:
                default:
                    return R.drawable.ic_edit;
            }
        }

        @AttrRes
        private static int kindColor(
                final ChangedFile.Kind kind
        ) {
            switch (kind) {
                case ADDED:
                    return R.attr.colorGitAdded;
                case DELETED:
                    return androidx.appcompat.R.attr.colorError;
                case UNTRACKED:
                    return androidx.appcompat.R.attr.colorPrimary;
                case CONFLICT:
                    return R.attr.colorGitConflict;
                case MODIFIED:
                default:
                    return R.attr.colorGitModified;
            }
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

    private static final class HeaderHolder extends RecyclerView.ViewHolder {

        private final ItemRepoHeaderBinding binding;

        private HeaderHolder(
                @NonNull final ItemRepoHeaderBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(
                final RepoDetailViewModel.UiState state,
                final Listener listener
        ) {
            final Context context = binding.getRoot().getContext();
            final RepoStatus status = state.status();
            binding.name.setText(state.name());
            binding.path.setText(state.path());
            binding.branch.setText(status == null ? "" : status.detached()
                    ? context.getString(R.string.repo_status_detached, status.branch())
                    : status.branch());
            binding.summary.setText(status == null ? "" : summary(context, state));
            binding.lfsNote.setVisibility(state.usesLfs() ? View.VISIBLE : View.GONE);
            bindPebble(state);
            bindOperation(context, state.active());
            bindFailure(context, state.failure(), listener);

            final boolean stacked = AdaptiveRow.apply(binding.actions);
            final int iconGravity = stacked ? MaterialButton.ICON_GRAVITY_TEXT_START : MaterialButton.ICON_GRAVITY_TOP;
            binding.actionUpdate.setIconGravity(iconGravity);
            binding.actionCommit.setIconGravity(iconGravity);
            binding.actionPush.setIconGravity(iconGravity);

            final boolean idle = state.active() == null && status != null;
            binding.actionUpdate.setEnabled(idle);
            binding.actionCommit.setEnabled(idle);
            binding.actionPush.setEnabled(idle);
            emphasise(state);
            binding.actionUpdate.setOnClickListener(button -> listener.onUpdate());
            binding.actionCommit.setOnClickListener(button -> listener.onCommit());
            binding.actionPush.setOnClickListener(button -> listener.onPush());
        }

        private void bindPebble(
                final RepoDetailViewModel.UiState state
        ) {
            final PebbleView pebble = binding.pebble;
            final OperationEntry active = state.active();
            if (active != null) {
                pebble.setStatus(PebbleView.Status.RUNNING);
                final OperationEntry.Progress progress = active.progress();
                if (progress == null || progress.fraction() == GitProgress.UNKNOWN) {
                    pebble.setIndeterminate(true);
                } else {
                    pebble.setProgress(progress.fraction());
                }
                return;
            }
            final RepoStatus status = state.status();
            if (status == null) {
                pebble.setStatus(PebbleView.Status.CLEAN);
                return;
            }
            final boolean hasChanges = state.tree() != null && !state.tree().isClean();
            switch (status.health()) {
                case CONFLICT:
                    pebble.setStatus(PebbleView.Status.CONFLICT);
                    break;
                case CHANGED:
                    pebble.setStatus(PebbleView.Status.CHANGED);
                    break;
                case DIVERGED:
                    pebble.setStatus(PebbleView.Status.DIVERGED);
                    break;
                case BEHIND:
                    pebble.setStatus(PebbleView.Status.BEHIND);
                    break;
                case AHEAD:
                    pebble.setStatus(PebbleView.Status.AHEAD);
                    break;
                case CLEAN:
                default:
                    pebble.setStatus(hasChanges ? PebbleView.Status.CHANGED : PebbleView.Status.CLEAN);
                    break;
            }
        }

        private void bindOperation(
                final Context context,
                final OperationEntry active
        ) {
            if (active == null) {
                binding.operation.setVisibility(View.GONE);
                return;
            }
            binding.operation.setVisibility(View.VISIBLE);
            binding.operationText.setText(context.getString(R.string.repo_operation_running,
                    OperationTexts.kind(context, active.kind()), OperationTexts.status(context, active)));
            final OperationEntry.Progress progress = active.progress();
            if (progress == null || progress.fraction() == GitProgress.UNKNOWN) {
                binding.operationProgress.setIndeterminate(true);
            } else {
                binding.operationProgress.setIndeterminate(false);
                binding.operationProgress.setProgressCompat(Math.round(progress.fraction() * PERCENT), true);
            }
        }

        private void bindFailure(
                final Context context,
                final OperationEntry failure,
                final Listener listener
        ) {
            if (failure == null || failure.failure() == null) {
                binding.failure.setVisibility(View.GONE);
                return;
            }
            binding.failure.setVisibility(View.VISIBLE);
            binding.failureText.setText(OperationTexts.failure(context, failure.failure()));
            binding.failureDismiss.setOnClickListener(button -> listener.onFailureDismiss());
            final GitFailureKind kind = failure.failure().kind();
            final int actionText;
            if (kind == GitFailureKind.NOT_FAST_FORWARD) {
                actionText = R.string.repo_banner_update;
            } else if (kind == GitFailureKind.DIRTY_TREE && failure.kind() == OperationKind.UPDATE) {
                actionText = R.string.repo_banner_stash_update;
            } else if (kind == GitFailureKind.CONFLICT) {
                actionText = R.string.conflicts_open;
            } else if (kind == GitFailureKind.HOST_KEY_UNKNOWN || kind == GitFailureKind.HOST_KEY_CHANGED) {
                actionText = R.string.repo_banner_check_server;
            } else if (kind == GitFailureKind.SSH_NO_KEY || kind == GitFailureKind.SSH_REJECTED) {
                actionText = R.string.ssh_keys_title;
            } else if (kind == GitFailureKind.METERED_NETWORK) {
                actionText = R.string.repo_banner_metered_retry;
            } else {
                actionText = 0;
            }
            if (actionText == 0) {
                binding.failureAction.setVisibility(View.GONE);
            } else {
                binding.failureAction.setVisibility(View.VISIBLE);
                binding.failureAction.setText(actionText);
                binding.failureAction.setOnClickListener(button -> listener.onFailureAction());
            }
        }

        /** Hebt die Aktion hervor, die der Zustand des Repos als Nächstes nahelegt. */
        private void emphasise(
                final RepoDetailViewModel.UiState state
        ) {
            MaterialButton primary = null;
            final RepoStatus status = state.status();
            if (status != null && !status.conflicted()) {
                final boolean changes = state.tree() != null
                        && (!state.tree().staged().isEmpty() || !state.tree().unstaged().isEmpty() || !state.tree().untracked().isEmpty());
                if (changes) {
                    primary = binding.actionCommit;
                } else if (status.behind() > 0) {
                    primary = binding.actionUpdate;
                } else if (status.ahead() > 0 || !status.hasUpstream()) {
                    primary = binding.actionPush;
                }
            }
            for (final MaterialButton button : new MaterialButton[] {binding.actionUpdate, binding.actionCommit, binding.actionPush}) {
                final boolean filled = button == primary;
                final Context context = button.getContext();
                final int onSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0);
                final int background = MaterialColors.getColor(context,
                        filled ? androidx.appcompat.R.attr.colorPrimary : com.google.android.material.R.attr.colorSecondaryContainer, 0);
                final int text = MaterialColors.getColor(context,
                        filled ? com.google.android.material.R.attr.colorOnPrimary : com.google.android.material.R.attr.colorOnSecondaryContainer, 0);
                // Mit einer einzigen Farbe sähen die Knöpfe während eines Vorgangs noch bedienbar aus, ohne es zu sein.
                button.setBackgroundTintList(whenDisabled(background, ColorUtils.setAlphaComponent(onSurface, DISABLED_BACKGROUND_ALPHA)));
                button.setTextColor(whenDisabled(text, ColorUtils.setAlphaComponent(onSurface, DISABLED_CONTENT_ALPHA)));
                button.setIconTint(whenDisabled(text, ColorUtils.setAlphaComponent(onSurface, DISABLED_CONTENT_ALPHA)));
            }
        }

        private static ColorStateList whenDisabled(
                @ColorInt final int enabled,
                @ColorInt final int disabled
        ) {
            return new ColorStateList(
                    new int[][] {new int[] {-android.R.attr.state_enabled}, new int[0]},
                    new int[] {disabled, enabled});
        }

        private static String summary(
                final Context context,
                final RepoDetailViewModel.UiState state
        ) {
            final RepoStatus status = state.status();
            final List<String> parts = new ArrayList<>();
            if (status.ahead() > 0) {
                parts.add(context.getString(R.string.repo_status_ahead, status.ahead()));
            }
            if (status.behind() > 0) {
                parts.add(context.getString(R.string.repo_status_behind, status.behind()));
            }
            final int changed = state.tree() == null ? 0 : state.tree().staged().size()
                    + state.tree().unstaged().size() + state.tree().untracked().size();
            if (changed > 0) {
                parts.add(context.getString(R.string.repo_status_changed, changed));
            }
            if (status.conflicted()) {
                parts.add(context.getString(R.string.repo_status_conflict));
            } else if (state.tree() != null && state.tree().inProgress()) {
                parts.add(context.getString(R.string.repo_status_in_progress));
            }
            if (!status.hasUpstream() && !status.detached()) {
                parts.add(context.getString(R.string.repo_status_no_upstream));
            }
            if (parts.isEmpty()) {
                parts.add(context.getString(R.string.repo_status_clean));
            }
            return String.join(" · ", parts);
        }
    }
}
