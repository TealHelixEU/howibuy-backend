package eu.tealhelix.howibuy.v1.types;

/**
 * Whether a basket could be judged at all.
 */
public enum BasketAssessmentOutcomeType {
	/**
	 * At least one product of the basket has an Overall Score, so the basket has a grade.
	 */
	ASSESSED,
	/**
	 * No product of the basket has an Overall Score, e.g. the basket is empty or holds only unknown or unclassified
	 * products, so the basket has no grade and earns no XP.
	 */
	NOTHING_TO_ASSESS
}
