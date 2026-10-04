package de.lembergmax.gitmax.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

public final class TextFileIoTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readsPlainUtf8TextWithUmlauts() throws Exception {
        final File file = file("a.txt", "Grüße\nzweite Zeile\n");

        final TextFileIo.Result result = TextFileIo.read(file, TextFileIo.VIEW_LIMIT_BYTES);

        assertEquals(TextFileIo.Kind.TEXT, result.kind());
        assertEquals("Grüße\nzweite Zeile\n", result.text());
        assertEquals(TextFileIo.LineEnding.LF, result.lineEnding());
        assertFalse(result.bom());
        assertTrue(result.isEditable());
    }

    @Test
    public void crlfIsNormalisedForTheEditorAndRestoredOnSave() throws Exception {
        final File file = file("crlf.txt", "eins\r\nzwei\r\n");
        final TextFileIo.Result result = TextFileIo.read(file, TextFileIo.VIEW_LIMIT_BYTES);
        assertEquals(TextFileIo.LineEnding.CRLF, result.lineEnding());
        assertEquals("eins\nzwei\n", result.text());

        TextFileIo.write(file, "eins\nzwei\ndrei\n", result.lineEnding(), result.bom());

        assertArrayEquals("eins\r\nzwei\r\ndrei\r\n".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file.toPath()));
    }

    @Test
    public void aBomIsKeptAcrossASave() throws Exception {
        final byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        final File file = new File(folder.getRoot(), "bom.txt");
        Files.write(file.toPath(), concat(bom, "text\n".getBytes(StandardCharsets.UTF_8)));
        final TextFileIo.Result result = TextFileIo.read(file, TextFileIo.VIEW_LIMIT_BYTES);
        assertTrue(result.bom());
        assertEquals("text\n", result.text());

        TextFileIo.write(file, "neu\n", result.lineEnding(), result.bom());

        assertArrayEquals(concat(bom, "neu\n".getBytes(StandardCharsets.UTF_8)), Files.readAllBytes(file.toPath()));
    }

    @Test
    public void aMissingTrailingNewlineStaysMissing() throws Exception {
        final File file = file("ohne.txt", "ohne Zeilenumbruch");
        final TextFileIo.Result result = TextFileIo.read(file, TextFileIo.VIEW_LIMIT_BYTES);

        TextFileIo.write(file, result.text() + "!", result.lineEnding(), result.bom());

        assertEquals("ohne Zeilenumbruch!", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void aNullByteMakesTheFileBinary() throws Exception {
        final File file = new File(folder.getRoot(), "bild.bin");
        Files.write(file.toPath(), new byte[] {'P', 'N', 'G', 0, 1, 2});

        final TextFileIo.Result result = TextFileIo.read(file, TextFileIo.VIEW_LIMIT_BYTES);

        assertEquals(TextFileIo.Kind.BINARY, result.kind());
        assertFalse(result.isEditable());
    }

    @Test
    public void invalidUtf8IsReportedInsteadOfBeingGarbled() throws Exception {
        final File file = new File(folder.getRoot(), "latin1.txt");
        Files.write(file.toPath(), new byte[] {'c', 'a', 'f', (byte) 0xE9, '\n'});

        final TextFileIo.Result result = TextFileIo.read(file, TextFileIo.VIEW_LIMIT_BYTES);

        assertEquals(TextFileIo.Kind.NOT_UTF8, result.kind());
    }

    @Test
    public void mixedLineEndingsAreNotEditable() throws Exception {
        final File file = file("mix.txt", "a\r\nb\nc\r\n");

        final TextFileIo.Result result = TextFileIo.read(file, TextFileIo.VIEW_LIMIT_BYTES);

        assertEquals(TextFileIo.LineEnding.MIXED, result.lineEnding());
        assertFalse(result.isEditable());
    }

    @Test
    public void filesOverTheLimitAreNotRead() throws Exception {
        final File file = file("gross.txt", "x".repeat(100));

        final TextFileIo.Result result = TextFileIo.read(file, 50);

        assertEquals(TextFileIo.Kind.TOO_LARGE, result.kind());
        assertEquals(100, result.size());
    }

    @Test
    public void filesOverTheEditLimitAreReadOnly() throws Exception {
        final File file = file("mittel.txt", "x".repeat((int) TextFileIo.EDIT_LIMIT_BYTES + 1));

        final TextFileIo.Result result = TextFileIo.read(file, TextFileIo.VIEW_LIMIT_BYTES);

        assertEquals(TextFileIo.Kind.TEXT, result.kind());
        assertFalse(result.isEditable());
    }

    @Test
    public void savingLeavesNoTempFileBehind() throws Exception {
        final File file = file("a.txt", "alt\n");

        TextFileIo.write(file, "neu\n", TextFileIo.LineEnding.LF, false);

        assertEquals(Arrays.asList("a.txt"), Arrays.asList(folder.getRoot().list()));
    }

    @Test
    public void changeDetectionNoticesAFileThatWasEditedMeanwhile() throws Exception {
        final File file = file("a.txt", "alt\n");
        final TextFileIo.Result result = TextFileIo.read(file, TextFileIo.VIEW_LIMIT_BYTES);
        assertFalse(TextFileIo.changedSince(file, result));

        Files.write(file.toPath(), "anderer Inhalt\n".getBytes(StandardCharsets.UTF_8));

        assertTrue(TextFileIo.changedSince(file, result));
    }

    private File file(
            final String name,
            final String content
    ) throws Exception {
        final File file = new File(folder.getRoot(), name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static byte[] concat(
            final byte[] first,
            final byte[] second
    ) {
        final byte[] joined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }
}
