package de.lembergmax.gitmax.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;
import java.util.function.Function;

public final class RepoNamingTest {

    private static final RemoteUrl URL = RemoteUrl.parse("https://github.com/lembergmax/tool.git").orElseThrow();

    @Test
    public void usesPlainRepoNameWhenFree() {
        final RepoNaming.Result result = RepoNaming.resolve(URL, name -> RepoNaming.FolderState.MISSING);

        assertEquals("tool", result.folderName());
        assertFalse(result.alreadyCloned());
    }

    @Test
    public void reusesFolderThatAlreadyHoldsTheSameRepo() {
        final RepoNaming.Result result = RepoNaming.resolve(URL, state(Map.of("tool", RepoNaming.FolderState.SAME_REPO)));

        assertEquals("tool", result.folderName());
        assertTrue(result.alreadyCloned());
    }

    @Test
    public void appendsOwnerWhenFolderBelongsToSomethingElse() {
        final RepoNaming.Result result = RepoNaming.resolve(URL, state(Map.of("tool", RepoNaming.FolderState.OTHER)));

        assertEquals("tool-lembergmax", result.folderName());
        assertFalse(result.alreadyCloned());
    }

    @Test
    public void recognizesSameRepoUnderOwnerSuffix() {
        final RepoNaming.Result result = RepoNaming.resolve(URL, state(Map.of(
                "tool", RepoNaming.FolderState.OTHER,
                "tool-lembergmax", RepoNaming.FolderState.SAME_REPO
        )));

        assertEquals("tool-lembergmax", result.folderName());
        assertTrue(result.alreadyCloned());
    }

    @Test
    public void fallsBackToNumberedNames() {
        final RepoNaming.Result result = RepoNaming.resolve(URL, state(Map.of(
                "tool", RepoNaming.FolderState.OTHER,
                "tool-lembergmax", RepoNaming.FolderState.OTHER,
                "tool-2", RepoNaming.FolderState.OTHER
        )));

        assertEquals("tool-3", result.folderName());
    }

    @Test
    public void failsWhenEveryCandidateIsTaken() {
        assertThrows(
                IllegalStateException.class,
                () -> RepoNaming.resolve(URL, name -> RepoNaming.FolderState.OTHER)
        );
    }

    @Test
    public void sanitizeReplacesCharactersThePhoneStorageRejects() {
        assertEquals("a_b_c_d_e_f_g_h_i", RepoNaming.sanitize("a:b*c?d\"e<f>g|h\\i"));
    }

    @Test
    public void sanitizeKeepsUmlautsAndEmoji() {
        assertEquals("Übung-😀", RepoNaming.sanitize("Übung-😀"));
    }

    @Test
    public void sanitizeNeverReturnsEmptyOrDotOnlyNames() {
        assertEquals("repo", RepoNaming.sanitize(""));
        assertEquals("repo", RepoNaming.sanitize("   "));
        assertEquals("repo", RepoNaming.sanitize(".."));
    }

    private static Function<String, RepoNaming.FolderState> state(
            final Map<String, RepoNaming.FolderState> known
    ) {
        return name -> known.getOrDefault(name, RepoNaming.FolderState.MISSING);
    }
}
