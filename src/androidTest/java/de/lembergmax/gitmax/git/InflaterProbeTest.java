package de.lembergmax.gitmax.git;

import org.eclipse.jgit.lib.InflaterCache;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Hält fest, was auf Android anders ist als im JDK: {@code InflaterInputStream.close()} beendet auch einen
 * übergebenen Inflater. JGit liest große Objekte so und gibt den Inflater danach an {@link InflaterCache} zurück.
 */
public final class InflaterProbeTest {

    @Test
    public void aStreamThatWasClosedNeverLeavesADeadInflaterInTheCache() throws Exception {
        final ByteArrayOutputStream packed = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(packed, new Deflater(1))) {
            out.write(new byte[100_000]);
        }
        final Inflater inflater = InflaterCache.get();
        final InputStream in = new InflaterInputStream(new ByteArrayInputStream(packed.toByteArray()), inflater, 8192);
        in.transferTo(OutputStream.nullOutputStream());
        in.close();

        InflaterCache.release(inflater);

        // Der Strom hat ihn beendet, aber der Cache gibt nur lebendige Inflater heraus.
        final Inflater next = InflaterCache.get();
        next.reset();
        InflaterCache.release(next);
    }
}
