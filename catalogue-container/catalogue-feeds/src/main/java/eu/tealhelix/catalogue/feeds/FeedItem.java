package eu.tealhelix.catalogue.feeds;

/**
 * One record of a Snapshot or a Delta Feed, read either into a {@link FeedRecord} or into a {@link RejectedRecord}.
 */
public sealed interface FeedItem permits FeedRecord, RejectedRecord {
	/**
	 * Where the record is in the Feed, counting the records from 1.
	 */
	long position();
}
