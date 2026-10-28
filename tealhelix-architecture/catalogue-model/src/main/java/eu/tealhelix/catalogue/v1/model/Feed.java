package eu.tealhelix.catalogue.v1.model;

import java.time.LocalDateTime;

import eu.tealhelix.catalogue.v1.types.FeedId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.HasFeedId;
import eu.tealhelix.common.v1.types.RetailerId;
import org.immutables.value.Value;

/**
 * A file a Retailer sent, stored once whatever the number of Catalogues it is for; each of them processes it in an
 * Ingestion Run of its own.
 */
@Value.Immutable
public interface Feed extends HasFeedId {
	@Override
	FeedId getId();

	RetailerId getRetailerId();

	FeedType getFeedType();

	LocalDateTime getReceivedAt();

	/**
	 * Where the Feed is stored, gzip-compressed, relative to the directory of the feed store.
	 */
	String getFilePath();

	/**
	 * The size of the Feed as received, before compression, in bytes.
	 */
	long getFileSize();

	/**
	 * The SHA-256 of the Feed as received, before compression, in lowercase hex.
	 */
	String getFileSha256();
}
