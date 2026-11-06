package eu.tealhelix.howibuy.v1.types;

/**
 * How well a basket matches the user's own priorities, judged on the personal Overall Scores of its products.
 */
public enum BasketMatch {
	/**
	 * The basket matches the user's priorities excellently.
	 */
	EXCELLENT,
	/**
	 * The basket matches the user's priorities well.
	 */
	GOOD,
	/**
	 * The basket matches the user's priorities poorly, and the user is advised to improve it.
	 */
	NEEDS_IMPROVEMENT
}
