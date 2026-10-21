package eu.tealhelix.catalogue.feeds;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * A Retailer Product and its Listing, as a Feed tells them. The values are the Feed's own, not yet validated, so any of
 * them may be missing.
 *
 * @param position        Where the record is in the Feed, counting the records from 1
 * @param productKey      The Retailer's own id of the product
 * @param names           The names of the product, by ISO 639-1 language code
 * @param characteristics The language-neutral characteristics of the product
 * @param tags            The language-neutral tags of the product
 * @param price           The price in the Catalogue of the Feed
 * @param currency        The ISO 4217 code of the currency of the price
 */
public record FeedRecord(
		long position,
		String productKey,
		Map<String, String> names,
		Map<String, String> characteristics,
		List<String> tags,
		BigDecimal price,
		String currency
) implements FeedItem {
}
