package de.lembergmax.gitmax.ui.accounts;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.databinding.ItemAccountBinding;
import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.ProviderType;

import java.util.Objects;

/**
 * Zeigt die verknüpften Konten als Liste; Konten mit Problem tragen eine Statusmarke.
 */
final class AccountAdapter extends ListAdapter<Account, AccountAdapter.Holder> {

    /** Wird beim Antippen eines Kontos aufgerufen. */
    interface OnAccountClick {

        void onAccountClick(
                @NonNull Account account
        );
    }

    private static final DiffUtil.ItemCallback<Account> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(
                @NonNull final Account oldItem,
                @NonNull final Account newItem
        ) {
            return oldItem.id().equals(newItem.id());
        }

        @Override
        public boolean areContentsTheSame(
                @NonNull final Account oldItem,
                @NonNull final Account newItem
        ) {
            return oldItem.equals(newItem);
        }
    };

    private final OnAccountClick listener;

    AccountAdapter(
            @NonNull final OnAccountClick listener
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
        return new Holder(ItemAccountBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(
            @NonNull final Holder holder,
            final int position
    ) {
        holder.bind(getItem(position), listener);
    }

    /** Eine Kontozeile. */
    static final class Holder extends RecyclerView.ViewHolder {

        private final ItemAccountBinding binding;

        Holder(
                @NonNull final ItemAccountBinding binding
        ) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(
                @NonNull final Account account,
                @NonNull final OnAccountClick listener
        ) {
            final Context context = binding.getRoot().getContext();
            final ProviderType provider = account.endpoint().provider();

            binding.tile.setText(provider == ProviderType.GITHUB ? "GH" : "GL");
            binding.login.setText(account.login());
            binding.host.setText(context.getString(
                    R.string.accounts_row_description, provider.displayName(), account.endpoint().host()));

            final int statusText = statusText(account.status());
            if (statusText == 0) {
                binding.status.setVisibility(android.view.View.GONE);
            } else {
                binding.status.setText(statusText);
                binding.status.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_error, 0, 0, 0);
                TextViewCompat.setCompoundDrawableTintList(binding.status, binding.status.getTextColors());
                binding.status.setVisibility(android.view.View.VISIBLE);
            }

            binding.getRoot().setContentDescription(describe(context, account, statusText));
            binding.getRoot().setOnClickListener(view -> listener.onAccountClick(account));
        }

        private static int statusText(
                final Account.Status status
        ) {
            switch (status) {
                case TOKEN_REJECTED:
                    return R.string.accounts_status_rejected;
                case SECRET_LOST:
                    return R.string.accounts_status_secret_lost;
                case ACTIVE:
                default:
                    return 0;
            }
        }

        private static String describe(
                final Context context,
                final Account account,
                final int statusText
        ) {
            final String base = account.login() + ", " + account.endpoint().provider().displayName();
            return statusText == 0 ? base : base + ", " + context.getString(statusText);
        }
    }
}
