package eu.tealhelix.howibuy.v1.types;

/**
 * Qualifies an alternative for a product.
 */
public enum AlternativeForProductType {
	/**
	 * A normal suggestion that would improve the user's score.
	 */
	SUGGESTION,
	/**
	 * The original product is good enough, no reason to suggest an alternative.
	 */
	GOOD_ENOUGH,
	/**
	 * The system could not produce a suggestion.
	 */
	NO_SUGGESTION
}
