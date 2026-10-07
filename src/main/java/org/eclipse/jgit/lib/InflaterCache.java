/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.util.zip.Inflater;

/**
 * Ersetzt die gleichnamige JGit-Klasse (sie wird beim Bau aus dem JGit-Jar entfernt).
 * <p>
 * Der Unterschied zum Original: Die Inflater aus {@link #get()} ignorieren {@code end()} von außen. Androids
 * {@code InflaterInputStream.close()} beendet nämlich auch einen übergebenen Inflater, JGit liest große Objekte aber
 * über genau so einen Strom und legt den Inflater vor oder nach dem Schließen in den Cache zurück. Im Original wirft
 * {@code reset()} oder das nächste {@code inflate()} dann „Inflater has been closed“, und jeder Klon (oder Push) mit
 * einer Datei über der Stromgrenze bricht ab. Freigegeben wird ein Inflater nur hier, über {@link PooledInflater}.
 */
public class InflaterCache {

	private static final int SZ = 4;

	private static final Inflater[] inflaterCache;

	private static int openInflaterCount;

	static {
		inflaterCache = new Inflater[SZ];
	}

	/**
	 * Obtain an Inflater for decompression.
	 *
	 * @return an available inflater. Never null.
	 */
	public static Inflater get() {
		final Inflater r = getImpl();
		return r != null ? r : new PooledInflater();
	}

	private static synchronized Inflater getImpl() {
		if (openInflaterCount > 0) {
			final Inflater r = inflaterCache[--openInflaterCount];
			inflaterCache[openInflaterCount] = null;
			return r;
		}
		return null;
	}

	/**
	 * Release an inflater previously obtained from this cache.
	 *
	 * @param i
	 *            the inflater to return. May be null, in which case this method
	 *            does nothing.
	 */
	public static void release(Inflater i) {
		if (i != null) {
			try {
				i.reset();
			} catch (NullPointerException alreadyEnded) {
				// Der Strom hat den Inflater schon beendet; ein beendeter Inflater darf nicht in den Cache.
				return;
			}
			if (releaseImpl(i)) {
				if (i instanceof PooledInflater pooled) {
					pooled.release();
				} else {
					i.end();
				}
			}
		}
	}

	private static synchronized boolean releaseImpl(Inflater i) {
		if (openInflaterCount < SZ) {
			inflaterCache[openInflaterCount++] = i;
			return false;
		}
		return true;
	}

	/** Ein Inflater, den nur der Cache beenden kann. */
	private static final class PooledInflater extends Inflater {

		PooledInflater() {
			super(false);
		}

		@Override
		public void end() {
			// Ignoriert: Androids InflaterInputStream.close() ruft das auf, obwohl der Inflater dem Cache gehört.
		}

		void release() {
			super.end();
		}
	}

	private InflaterCache() {
		throw new UnsupportedOperationException();
	}
}
