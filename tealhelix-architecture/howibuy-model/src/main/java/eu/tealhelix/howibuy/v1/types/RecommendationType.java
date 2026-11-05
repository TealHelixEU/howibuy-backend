package eu.tealhelix.howibuy.v1.types;

/**
 * The assessment's answer to "is there something better to buy?" for a Reference Product.
 */
public enum RecommendationType {
	/**
	 * Alternatives are offered: the Reference Product does not pass the Gate, and at least one Pool holds something
	 * better.
	 */
	RECOMMENDED,
	/**
	 * The Reference Product passes the Gate, so no Alternative is sought.
	 */
	GOOD_ENOUGH,
	/**
	 * The Reference Product does not pass the Gate, and every Pool is empty. Nothing better being stocked does not make
	 * the Reference Product good.
	 */
	NO_ALTERNATIVE,
	/**
	 * The Reference Product has no Nutri-Score, so it has no Overall Score and nothing is said about it.
	 */
	NO_SCORE
}
