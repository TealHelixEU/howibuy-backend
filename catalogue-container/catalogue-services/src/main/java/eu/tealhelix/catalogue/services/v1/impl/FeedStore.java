package eu.tealhelix.catalogue.services.v1.impl;

import java.io.InputStream;

import eu.tealhelix.catalogue.v1.types.FeedId;
import eu.tealhelix.common.v1.types.RetailerId;

/**
 * Keeps the Feeds as they were received, gzip-compressed, so they can be processed and re-run later.
 */
public interface FeedStore {
	/**
	 * Store a Feed, reading its body to the end as it arrives. The Feed appears in the store only once it was read whole;
	 * if reading fails, nothing of it is left in the store.
	 *
	 * @param retailerId The Retailer that sent the Feed
	 * @param feedId     The Feed
	 * @param body       The Feed as the Retailer sent it
	 * @return Where the Feed is stored, and its size and checksum as received
	 * @throws java.io.UncheckedIOException If the body cannot be read or the Feed cannot be written
	 */
	StoredFeed store(RetailerId retailerId, FeedId feedId, InputStream body);

	/**
	 * Open a stored Feed to read it as it was received.
	 *
	 * @param path Where the Feed is stored, as {@link StoredFeed#path()} tells
	 * @return The Feed, decompressed; the caller closes it
	 * @throws java.io.UncheckedIOException If the Feed cannot be opened
	 */
	InputStream open(String path);

	/**
	 * Tell whether a Feed is stored.
	 *
	 * @param path Where the Feed is stored, as {@link StoredFeed#path()} tells
	 * @return Whether it is there
	 */
	boolean exists(String path);

	/**
	 * Remove a stored Feed; nothing happens if it is not there.
	 *
	 * @param path Where the Feed is stored, as {@link StoredFeed#path()} tells
	 * @throws java.io.UncheckedIOException If the Feed cannot be removed
	 */
	void delete(String path);
}
