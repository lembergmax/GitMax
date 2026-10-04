package de.lembergmax.gitmax.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Optional;

/** Prüft die Erkennung von LFS-Zeigern und LFS-Mustern. */
public final class GitLfsTest {

    private static final String OID = "4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393";

    @Test
    public void parsesAStandardPointer() {
        final Optional<GitLfs.Pointer> pointer = GitLfs.parsePointer(
                "version https://git-lfs.github.com/spec/v1\noid sha256:" + OID + "\nsize 12345\n");

        assertTrue(pointer.isPresent());
        assertEquals(OID, pointer.get().oid());
        assertEquals(12345L, pointer.get().size());
    }

    @Test
    public void acceptsThePointerWithoutTrailingNewline() {
        assertTrue(GitLfs.parsePointer("version https://git-lfs.github.com/spec/v1\noid sha256:" + OID + "\nsize 1")
                .isPresent());
    }

    @Test
    public void acceptsTheLegacyVersionLine() {
        assertTrue(GitLfs.parsePointer("version https://hawser.github.com/spec/v1\noid sha256:" + OID + "\nsize 9\n")
                .isPresent());
    }

    @Test
    public void ignoresExtensionLinesBetweenTheFields() {
        assertTrue(GitLfs.parsePointer(
                "version https://git-lfs.github.com/spec/v1\next-0-foo sha256:" + OID + "\noid sha256:" + OID
                        + "\nsize 7\n").isPresent());
    }

    @Test
    public void rejectsTextThatOnlyLooksSimilar() {
        assertFalse(GitLfs.parsePointer("").isPresent());
        assertFalse(GitLfs.parsePointer("hallo welt\n").isPresent());
        assertFalse(GitLfs.parsePointer("version https://git-lfs.github.com/spec/v1\nsize 5\n").isPresent());
        assertFalse(GitLfs.parsePointer("version https://git-lfs.github.com/spec/v1\noid sha256:" + OID).isPresent());
        assertFalse(GitLfs.parsePointer("version https://git-lfs.github.com/spec/v1\noid sha256:abc\nsize 5\n").isPresent());
        assertFalse(GitLfs.parsePointer("version https://git-lfs.github.com/spec/v1\noid sha256:" + OID.toUpperCase()
                + "\nsize 5\n").isPresent());
        assertFalse(GitLfs.parsePointer("version https://git-lfs.github.com/spec/v1\noid sha256:" + OID + "\nsize x\n")
                .isPresent());
        assertFalse(GitLfs.parsePointer("version https://git-lfs.github.com/spec/v1\noid sha256:" + OID + "\nsize -4\n")
                .isPresent());
    }

    @Test
    public void largeTextIsNeverAPointer() {
        final String padding = "x".repeat(2000);

        assertFalse(GitLfs.parsePointer(
                "version https://git-lfs.github.com/spec/v1\noid sha256:" + OID + "\nsize 5\n" + padding).isPresent());
    }

    @Test
    public void detectsTheLfsFilterInGitAttributes() {
        assertTrue(GitLfs.usesLfs("*.psd filter=lfs diff=lfs merge=lfs -text\n"));
        assertTrue(GitLfs.usesLfs("# Bilder\n*.png -text filter=lfs\n"));
        assertTrue(GitLfs.usesLfs("*.bin\tfilter=lfs\r\n"));
    }

    @Test
    public void otherAttributesAreNotLfs() {
        assertFalse(GitLfs.usesLfs(""));
        assertFalse(GitLfs.usesLfs("* text=auto eol=lf\n"));
        assertFalse(GitLfs.usesLfs("# filter=lfs steht nur im Kommentar\n"));
        assertFalse(GitLfs.usesLfs("*.txt filter=lfsx\n"));
    }
}
