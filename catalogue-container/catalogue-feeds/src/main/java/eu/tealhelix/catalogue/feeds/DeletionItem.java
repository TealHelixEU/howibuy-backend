package eu.tealhelix.catalogue.feeds;

/**
 * One record of a Deletion Feed, read either into a {@link DeletionRecord} or into a {@link RejectedRecord}.
 */
public sealed interface DeletionItem permits DeletionRecord, RejectedRecord {
	/**
	 * Where the record is in the Feed, counting the records from 1.
	 */
	long position();
}
