package eu.tealhelix.catalogue.services.v1.impl;

import java.io.InputStream;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;

/**
 * Keeps the Feeds as they were received, gzip-compressed, so they can be processed and re-run later.
 */
public interface FeedStore {
	/**
	 * Store the Feed of an Ingestion Run, reading its body to the end as it arrives. The Feed appears in the store only
	 * once it was read whole; if reading fails, nothing of it is left in the store.
	 *
	 * @param catalogueId The Catalogue the Feed is for
	 * @param runId       The run that received it
	 * @param body        The Feed
	 * @return Where the Feed is stored, and its size and checksum as received
	 * @throws java.io.UncheckedIOException If the body cannot be read or the Feed cannot be written
	 */
	StoredFeed store(CatalogueId catalogueId, IngestionRunId runId, InputStream body);

	/**
	 * Remove a stored Feed; nothing happens if it is not there.
	 *
	 * @param path Where the Feed is stored, as {@link StoredFeed#path()} tells
	 * @throws java.io.UncheckedIOException If the Feed cannot be removed
	 */
	void delete(String path);
}
