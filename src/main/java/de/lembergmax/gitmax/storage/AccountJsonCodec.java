package de.lembergmax.gitmax.storage;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.model.Account;
import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.ProviderType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Liest und schreibt Konten als JSON. Das Lesen ist tolerant: ein beschädigter Eintrag wird
 * übersprungen, die übrigen Konten bleiben erhalten, es wird nie geworfen. Token stehen nie hier.
 */
public final class AccountJsonCodec {

    private AccountJsonCodec() {
    }

    @NonNull
    public static String encode(
            @NonNull final List<Account> accounts
    ) {
        final JSONArray array = new JSONArray();
        for (final Account account : accounts) {
            try {
                final JSONObject node = new JSONObject();
                node.put("id", account.id());
                node.put("provider", account.endpoint().provider().name());
                node.put("host", account.endpoint().host());
                node.put("apiBaseUrl", account.endpoint().apiBaseUrl());
                node.put("login", account.login());
                node.put("userId", account.userId());
                node.put("name", account.name());
                node.put("identityName", account.identity().name());
                node.put("identityEmail", account.identity().email());
                node.put("scopes", new JSONArray(account.scopes()));
                node.put("status", account.status().name());
                array.put(node);
            } catch (final JSONException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        return array.toString();
    }

    @NonNull
    public static List<Account> decode(
            final String json
    ) {
        final List<Account> accounts = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return accounts;
        }
        final JSONArray array;
        try {
            array = new JSONArray(json);
        } catch (final JSONException malformed) {
            return accounts;
        }
        for (int index = 0; index < array.length(); index += 1) {
            final JSONObject node = array.optJSONObject(index);
            if (node != null) {
                decodeOne(node).ifPresent(accounts::add);
            }
        }
        return accounts;
    }

    private static Optional<Account> decodeOne(
            final JSONObject node
    ) {
        try {
            final ProviderType provider = ProviderType.valueOf(node.getString("provider"));
            final AccountEndpoint endpoint = new AccountEndpoint(
                    provider, node.getString("host"), node.getString("apiBaseUrl"));
            final List<String> scopes = new ArrayList<>();
            final JSONArray scopeArray = node.optJSONArray("scopes");
            if (scopeArray != null) {
                for (int index = 0; index < scopeArray.length(); index += 1) {
                    scopes.add(scopeArray.getString(index));
                }
            }
            return Optional.of(new Account(
                    node.getString("id"),
                    endpoint,
                    node.getString("login"),
                    node.getLong("userId"),
                    node.getString("name"),
                    new CommitIdentity(node.getString("identityName"), node.getString("identityEmail")),
                    scopes,
                    statusOf(node.optString("status"))
            ));
        } catch (final JSONException | IllegalArgumentException unreadable) {
            return Optional.empty();
        }
    }

    private static Account.Status statusOf(
            final String stored
    ) {
        try {
            return Account.Status.valueOf(stored);
        } catch (final IllegalArgumentException unknown) {
            return Account.Status.ACTIVE;
        }
    }
}
