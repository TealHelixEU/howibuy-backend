package eu.tealhelix.catalogue.services.v1.impl;

/**
 * What a valid record of a Feed did to its Retailer Product.
 */
public enum RecordOutcome {
	/** The product was new to the Catalogue of the Feed: it had no Listing there. */
	ADDED,
	/** Something visible changed: the product's content, the Listing's price or currency, or its reactivation. */
	CHANGED,
	/** Nothing changed. */
	UNCHANGED
}
