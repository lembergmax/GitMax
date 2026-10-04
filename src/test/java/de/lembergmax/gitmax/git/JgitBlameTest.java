package de.lembergmax.gitmax.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.GitFailureException;
import de.lembergmax.gitmax.domain.model.BlameLine;
import de.lembergmax.gitmax.domain.model.GitFailureKind;

import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

/** Prüft, dass Blame jede Zeile dem richtigen Commit und Autor zuordnet. */
public final class JgitBlameTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final JgitAdvanced advanced = new JgitAdvanced();
    private SystemReader originalReader;
    private TestRepo repo;

    @Before
    public void setUp() throws Exception {
        originalReader = SystemReader.getInstance();
        SystemReader.setInstance(new AppSystemReader(originalReader, folder.newFolder("git-home")));
        repo = TestRepo.init(folder.newFolder("repo"));
    }

    @After
    public void tearDown() {
        SystemReader.setInstance(originalReader);
    }

    @Test
    public void everyLineBelongsToTheCommitThatLastChangedIt() throws Exception {
        repo.commit("datei.txt", "eins\nzwei\ndrei\n", "Anfang", TestRepo.ANNA);
        final String first = repo.head();
        repo.commit("datei.txt", "eins\nZWEI\ndrei\nvier\n", "Zwei groß und vier", TestRepo.BERND);
        final String second = repo.head();

        final List<BlameLine> lines = advanced.blame(repo.directory(), "datei.txt");

        assertEquals(4, lines.size());
        assertLine(lines.get(0), 1, "eins", first, "Anna", "Anfang");
        assertLine(lines.get(1), 2, "ZWEI", second, "Bernd", "Zwei groß und vier");
        assertLine(lines.get(2), 3, "drei", first, "Anna", "Anfang");
        assertLine(lines.get(3), 4, "vier", second, "Bernd", "Zwei groß und vier");
        assertTrue(lines.get(0).timeMillis() > 0);
        assertEquals(second.substring(0, 7), lines.get(1).shortId());
    }

    @Test
    public void filesInSubfoldersWork() throws Exception {
        repo.commit("a/b/c.txt", "x\ny\n", "Tief", TestRepo.ANNA);

        final List<BlameLine> lines = advanced.blame(repo.directory(), "a/b/c.txt");

        assertEquals(List.of("x", "y"), lines.stream().map(BlameLine::text).toList());
    }

    @Test
    public void linesThatAreNotCommittedYetAreMarkedAsSuch() throws Exception {
        repo.commit("datei.txt", "eins\nzwei\n", "Anfang", TestRepo.ANNA);
        repo.write("datei.txt", "eins\nlokal geändert\n");

        final List<BlameLine> lines = advanced.blame(repo.directory(), "datei.txt");

        assertTrue(lines.get(0).isCommitted());
        assertEquals("lokal geändert", lines.get(1).text());
        assertFalse(lines.get(1).isCommitted());
        assertEquals("", lines.get(1).shortId());
    }

    @Test
    public void anUntrackedFileHasNoHistory() throws Exception {
        repo.commit("datei.txt", "eins\n", "Anfang", TestRepo.ANNA);
        repo.write("neu.txt", "neu\n");

        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> advanced.blame(repo.directory(), "neu.txt"));

        assertEquals(GitFailureKind.NOT_FOUND, failure.kind());
    }

    @Test
    public void aRepoWithoutCommitsHasNoHistory() {
        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> advanced.blame(repo.directory(), "datei.txt"));

        assertEquals(GitFailureKind.NOT_FOUND, failure.kind());
    }

    @Test
    public void veryLongFilesAreRefused() throws Exception {
        final StringBuilder text = new StringBuilder();
        for (int line = 0; line < BlameOperations.MAX_LINES + 1; line += 1) {
            text.append("z\n");
        }
        repo.commit("lang.txt", text.toString(), "Lang", TestRepo.ANNA);

        final GitFailureException failure = assertThrows(GitFailureException.class,
                () -> advanced.blame(repo.directory(), "lang.txt"));

        assertEquals(GitFailureKind.INVALID_PATH, failure.kind());
    }

    private static void assertLine(
            final BlameLine line,
            final int number,
            final String text,
            final String commit,
            final String author,
            final String subject
    ) {
        assertEquals(number, line.number());
        assertEquals(text, line.text());
        assertEquals(commit, line.commitId());
        assertEquals(author, line.authorName());
        assertEquals(subject, line.subject());
    }
}
