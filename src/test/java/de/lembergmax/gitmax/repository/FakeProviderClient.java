package de.lembergmax.gitmax.repository;

import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.NewRepo;
import de.lembergmax.gitmax.domain.model.RemoteRepo;
import de.lembergmax.gitmax.provider.GitProviderClient;
import de.lembergmax.gitmax.provider.ProviderException;
import de.lembergmax.gitmax.provider.ProviderProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Steuerbarer {@link GitProviderClient} für Tests der Repositories. */
final class FakeProviderClient implements GitProviderClient {

    private ProviderProfile profile;
    private ProviderException profileFailure;
    private List<RemoteRepo> repos = List.of();
    private ProviderException listFailure;
    private final List<String> tokensSeen = new ArrayList<>();
    private final List<NewRepo> created = new ArrayList<>();
    private ProviderException createFailure;
    private Function<NewRepo, RemoteRepo> creator;

    FakeProviderClient returnsProfile(
            final ProviderProfile newProfile
    ) {
        profile = newProfile;
        profileFailure = null;
        return this;
    }

    FakeProviderClient failsProfileWith(
            final ProviderException failure
    ) {
        profileFailure = failure;
        return this;
    }

    FakeProviderClient returnsRepos(
            final List<RemoteRepo> newRepos
    ) {
        repos = newRepos;
        listFailure = null;
        return this;
    }

    FakeProviderClient failsListWith(
            final ProviderException failure
    ) {
        listFailure = failure;
        return this;
    }

    /** So antwortet das Anlegen eines Repos: aus der Anfrage wird das neue Repo gebaut. */
    FakeProviderClient creates(
            final Function<NewRepo, RemoteRepo> newCreator
    ) {
        creator = newCreator;
        createFailure = null;
        return this;
    }

    FakeProviderClient failsCreateWith(
            final ProviderException failure
    ) {
        createFailure = failure;
        return this;
    }

    /** Die Anfragen, die zum Anlegen eines Repos kamen. */
    List<NewRepo> created() {
        return created;
    }

    List<String> tokensSeen() {
        return tokensSeen;
    }

    @Override
    public ProviderProfile fetchProfile(
            final AccountEndpoint endpoint,
            final String token
    ) throws ProviderException {
        tokensSeen.add(token);
        if (profileFailure != null) {
            throw profileFailure;
        }
        return profile;
    }

    @Override
    public List<RemoteRepo> listRepositories(
            final String accountId,
            final AccountEndpoint endpoint,
            final String token,
            final ListListener listener
    ) throws ProviderException {
        tokensSeen.add(token);
        if (listFailure != null) {
            throw listFailure;
        }
        return repos;
    }

    @Override
    public RemoteRepo createRepository(
            final String accountId,
            final AccountEndpoint endpoint,
            final String token,
            final NewRepo request
    ) throws ProviderException {
        tokensSeen.add(token);
        created.add(request);
        if (createFailure != null) {
            throw createFailure;
        }
        return creator.apply(request);
    }
}
