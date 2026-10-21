package eu.tealhelix.catalogue.feeds;

/**
 * A record of a Feed that could not be read.
 *
 * @param position   Where the record is in the Feed, counting the records from 1
 * @param productKey The Retailer's own id of the product; {@code null} if it could not be read
 * @param reason     Why the record could not be read
 */
public record RejectedRecord(long position, String productKey, String reason) implements FeedItem {
}
