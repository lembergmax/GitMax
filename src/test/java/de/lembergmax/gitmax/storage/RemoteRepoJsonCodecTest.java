package de.lembergmax.gitmax.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.lembergmax.gitmax.domain.model.RemoteRepo;

import org.junit.Test;

import java.util.List;

public final class RemoteRepoJsonCodecTest {

    private static final RemoteRepo REPO = new RemoteRepo(
            "acc", "7", "alpha", "max/alpha", "Ein Test", true, false, true, "main",
            "https://github.com/max/alpha.git", "git@github.com:max/alpha.git", "https://github.com/max/alpha", 1_790_000_000_000L);

    @Test
    public void roundTripKeepsEveryField() {
        final RemoteRepoJsonCodec.Snapshot decoded = RemoteRepoJsonCodec.decode(
                RemoteRepoJsonCodec.encode(new RemoteRepoJsonCodec.Snapshot(123L, List.of(REPO))));

        assertEquals(123L, decoded.fetchedAtMillis());
        assertEquals(List.of(REPO), decoded.repos());
    }

    @Test
    public void damagedEntryIsSkipped() {
        final String json = RemoteRepoJsonCodec.encode(new RemoteRepoJsonCodec.Snapshot(1L, List.of(REPO)))
                .replace("\"repos\":[", "\"repos\":[{\"name\":\"ohne-pflichtfelder\"},");

        assertEquals(List.of(REPO), RemoteRepoJsonCodec.decode(json).repos());
    }

    @Test
    public void garbageGivesAnEmptySnapshotWithTimeZero() {
        assertTrue(RemoteRepoJsonCodec.decode("kein json").repos().isEmpty());
        assertEquals(0L, RemoteRepoJsonCodec.decode(null).fetchedAtMillis());
    }
}
