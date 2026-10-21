package eu.tealhelix.catalogue.feeds;

/**
 * A Retailer Product that a Deletion Feed takes off sale in its Catalogue. The value is the Feed's own, not yet
 * validated, so it may be missing.
 *
 * @param position   Where the record is in the Feed, counting the records from 1
 * @param productKey The Retailer's own id of the product
 */
public record DeletionRecord(long position, String productKey) implements DeletionItem {
}
