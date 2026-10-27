package eu.tealhelix.howibuy.v1.types;

/**
 * Why the assessment could not tell which Archetype Product a scanned product is.
 */
public enum IdentificationFailureReason {
	/**
	 * The Retailer of the user's Catalogue has no product with this key in HowiBuy.
	 */
	UNKNOWN_PRODUCT,
	/**
	 * The product is known but was never classified.
	 */
	NOT_CLASSIFIED,
	/**
	 * The Classification of the product says that no Archetype Product matches it.
	 */
	NO_MATCHING_ARCHETYPE
}
