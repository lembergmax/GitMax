package de.lembergmax.gitmax.git;

import androidx.annotation.NonNull;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.RemoteUrl;
import de.lembergmax.gitmax.domain.model.GitFailureKind;
import de.lembergmax.gitmax.domain.model.RemoteInfo;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Remotes eines Repos: auflisten, hinzufügen, Adresse ändern, umbenennen, entfernen. */
final class RemoteOperations {

    private static final String SECTION = ConfigConstants.CONFIG_REMOTE_SECTION;
    private static final Pattern CREDENTIALS_IN_URL = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://[^/@\\s]*@.*$");

    RemoteOperations() {
    }

    @NonNull
    List<RemoteInfo> list(
            @NonNull final File repoDirectory
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final StoredConfig config = git.getRepository().getConfig();
            final List<RemoteInfo> remotes = new ArrayList<>();
            for (final String name : new TreeSet<>(config.getSubsections(SECTION))) {
                final String url = config.getString(SECTION, name, ConfigConstants.CONFIG_KEY_URL);
                remotes.add(new RemoteInfo(name, url == null ? "" : url));
            }
            return remotes;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void add(
            @NonNull final File repoDirectory,
            @NonNull final String name,
            @NonNull final String url
    ) throws GitFailureException {
        requireValidName(name);
        final String clean = cleanUrl(url);
        try (Git git = JgitEngine.open(repoDirectory)) {
            if (git.getRepository().getConfig().getSubsections(SECTION).contains(name)) {
                throw new GitFailureException(GitFailureKind.ALREADY_EXISTS, "Remote already exists: " + name, null);
            }
            git.remoteAdd().setName(name).setUri(new URIish(clean)).call();
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void setUrl(
            @NonNull final File repoDirectory,
            @NonNull final String name,
            @NonNull final String url
    ) throws GitFailureException {
        final String clean = cleanUrl(url);
        try (Git git = JgitEngine.open(repoDirectory)) {
            final StoredConfig config = git.getRepository().getConfig();
            if (!config.getSubsections(SECTION).contains(name)) {
                throw new GitFailureException(GitFailureKind.NOT_FOUND, "Remote not found: " + name, null);
            }
            config.setString(SECTION, name, ConfigConstants.CONFIG_KEY_URL, clean);
            config.save();
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void rename(
            @NonNull final File repoDirectory,
            @NonNull final String oldName,
            @NonNull final String newName
    ) throws GitFailureException {
        requireValidName(newName);
        try (Git git = JgitEngine.open(repoDirectory)) {
            final Repository repository = git.getRepository();
            final StoredConfig config = repository.getConfig();
            final Set<String> existing = config.getSubsections(SECTION);
            if (!existing.contains(oldName)) {
                throw new GitFailureException(GitFailureKind.NOT_FOUND, "Remote not found: " + oldName, null);
            }
            if (existing.contains(newName)) {
                throw new GitFailureException(GitFailureKind.ALREADY_EXISTS, "Remote already exists: " + newName, null);
            }
            final RemoteConfig remote = new RemoteConfig(config, oldName);
            final RemoteConfig renamed = new RemoteConfig(config, newName);
            for (final URIish uri : remote.getURIs()) {
                renamed.addURI(uri);
            }
            for (final org.eclipse.jgit.transport.RefSpec spec : remote.getFetchRefSpecs()) {
                renamed.addFetchRefSpec(new org.eclipse.jgit.transport.RefSpec(
                        spec.toString().replace(Constants.R_REMOTES + oldName + "/", Constants.R_REMOTES + newName + "/")));
            }
            config.unsetSection(SECTION, oldName);
            renamed.update(config);
            for (final String branch : config.getSubsections(ConfigConstants.CONFIG_BRANCH_SECTION)) {
                if (oldName.equals(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_REMOTE))) {
                    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_REMOTE, newName);
                }
            }
            config.save();
            moveTrackingRefs(repository, oldName, newName);
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    void remove(
            @NonNull final File repoDirectory,
            @NonNull final String name
    ) throws GitFailureException {
        try (Git git = JgitEngine.open(repoDirectory)) {
            final Repository repository = git.getRepository();
            if (!repository.getConfig().getSubsections(SECTION).contains(name)) {
                throw new GitFailureException(GitFailureKind.NOT_FOUND, "Remote not found: " + name, null);
            }
            git.remoteRemove().setRemoteName(name).call();
            for (final Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_REMOTES + name + "/")) {
                final RefUpdate update = repository.updateRef(ref.getName());
                update.setForceUpdate(true);
                update.delete();
            }
        } catch (final GitFailureException failure) {
            throw failure;
        } catch (final Exception failure) {
            throw GitErrorMapper.map(failure, false);
        }
    }

    private static void moveTrackingRefs(
            final Repository repository,
            final String oldName,
            final String newName
    ) throws IOException {
        for (final Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_REMOTES + oldName + "/")) {
            final String target = Constants.R_REMOTES + newName + "/" + ref.getName().substring((Constants.R_REMOTES + oldName + "/").length());
            final RefRename rename = repository.renameRef(ref.getName(), target);
            rename.rename();
        }
    }

    private static void requireValidName(
            final String name
    ) throws GitFailureException {
        if (name.isBlank() || name.contains(" ") || !Repository.isValidRefName(Constants.R_REMOTES + name + "/x")) {
            throw new GitFailureException(GitFailureKind.INVALID_NAME, "Invalid remote name: " + name, null);
        }
    }

    /** Entfernt Zugangsdaten aus der Adresse: Token gehören nie in die {@code .git/config}. */
    static String cleanUrl(
            final String url
    ) throws GitFailureException {
        final String trimmed = url.strip();
        if (trimmed.isEmpty()) {
            throw new GitFailureException(GitFailureKind.INVALID_NAME, "The address is empty", null);
        }
        final String clean = RemoteUrl.parse(trimmed).map(RemoteUrl::toCanonicalUrl).orElse(trimmed);
        if (CREDENTIALS_IN_URL.matcher(clean).matches()) {
            throw new GitFailureException(GitFailureKind.INVALID_NAME, "The address contains credentials", null);
        }
        return clean;
    }
}
