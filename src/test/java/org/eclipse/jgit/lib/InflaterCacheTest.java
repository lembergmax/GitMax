package org.eclipse.jgit.lib;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.zip.Inflater;

/** Prüft die Ersatz-Klasse: Ein vom Strom bereits beendeter Inflater darf nie wieder herausgegeben werden. */
public final class InflaterCacheTest {

    @Test
    public void aReleasedInflaterIsHandedOutAgain() {
        final Inflater inflater = InflaterCache.get();

        InflaterCache.release(inflater);

        assertSame(inflater, InflaterCache.get());
    }

    @Test
    public void anInflaterThatAStreamTriedToEndStaysUsable() {
        final Inflater inflater = InflaterCache.get();
        InflaterCache.release(inflater);

        inflater.end();

        final Inflater next = InflaterCache.get();
        next.reset();
        assertSame(inflater, next);
        InflaterCache.release(next);
    }

    @Test
    public void anInflaterThatWasEndedBeforeReleaseIsDroppedInsteadOfFailing() {
        final Inflater foreign = new Inflater(false);
        foreign.end();

        InflaterCache.release(foreign);

        assertNotSame(foreign, InflaterCache.get());
    }
}
