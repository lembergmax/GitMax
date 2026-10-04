package de.lembergmax.gitmax.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.AccountEndpoint;
import de.lembergmax.gitmax.domain.model.CommitIdentity;
import de.lembergmax.gitmax.domain.model.ProviderType;
import de.lembergmax.gitmax.provider.ProviderProfile;
import de.lembergmax.gitmax.storage.AesTestCipher;
import de.lembergmax.gitmax.storage.MemoryKeyValueStore;
import de.lembergmax.gitmax.storage.RepoSettingsStore;
import de.lembergmax.gitmax.storage.SecretVault;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class IdentityResolverTest {

    private static final AccountEndpoint GITHUB = AccountEndpoint.fromHostInput(ProviderType.GITHUB, "").orElseThrow();
    private static final AccountEndpoint GITLAB = AccountEndpoint.fromHostInput(ProviderType.GITLAB, "").orElseThrow();

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final MemoryKeyValueStore settingsStorage = new MemoryKeyValueStore();
    private FakeProviderClient client;
    private AccountRepository accounts;
    private IdentityResolver resolver;

    @Before
    public void setUp() {
        client = new FakeProviderClient();
        final AtomicInteger ids = new AtomicInteger();
        accounts = new AccountRepository(new MemoryKeyValueStore(),
                new SecretVault(new MemoryKeyValueStore(), new AesTestCipher()),
                provider -> client, () -> "konto-" + ids.incrementAndGet());
        resolver = new IdentityResolver(accounts, new RepoSettingsStore(settingsStorage));
    }

    @Test
    public void withoutAccountsAndChoiceThereIsNoIdentity() throws Exception {
        assertTrue(resolver.resolve(repoWithOrigin("https://github.com/max/alpha.git")).isEmpty());
    }

    @Test
    public void theAccountOfTheOriginHostSuppliesTheIdentity() throws Exception {
        connect(GITHUB, "max", "Max Hub", "hub@example.org");
        connect(GITLAB, "max", "Max Lab", "lab@example.org");

        final Optional<CommitIdentity> identity = resolver.resolve(repoWithOrigin("https://gitlab.com/max/app.git"));

        assertEquals(new CommitIdentity("Max Lab", "lab@example.org"), identity.orElseThrow());
    }

    @Test
    public void aRepoWithoutKnownHostFallsBackToTheFirstAccount() throws Exception {
        connect(GITHUB, "max", "Max Hub", "hub@example.org");

        final Optional<CommitIdentity> identity = resolver.resolve(repoWithOrigin("https://git.fremd.example/team/app.git"));

        assertEquals("Max Hub", identity.orElseThrow().name());
    }

    @Test
    public void theIdentityChosenForTheRepoWinsOverEveryAccount() throws Exception {
        connect(GITHUB, "max", "Max Hub", "hub@example.org");
        final File repo = repoWithOrigin("https://github.com/max/alpha.git");

        resolver.choose(repo, new CommitIdentity("Nur Hier", "hier@example.org"));

        assertEquals("Nur Hier", resolver.resolve(repo).orElseThrow().name());
        assertEquals("another repo is unaffected", "Max Hub",
                resolver.resolve(repoWithOrigin("https://github.com/max/beta.git")).orElseThrow().name());
    }

    private void connect(
            final AccountEndpoint endpoint,
            final String login,
            final String name,
            final String email
    ) throws Exception {
        client.returnsProfile(new ProviderProfile(login, endpoint.provider() == ProviderType.GITHUB ? 1L : 2L, name, email, List.of()));
        accounts.connect(endpoint, "token-" + login + endpoint.host());
    }

    private File repoWithOrigin(
            final String url
    ) throws Exception {
        final File repo = folder.newFolder();
        final File git = new File(repo, ".git");
        assertTrue(git.mkdirs());
        Files.write(new File(git, "config").toPath(),
                ("[remote \"origin\"]\n\turl = " + url + "\n").getBytes(StandardCharsets.UTF_8));
        return repo;
    }
}
