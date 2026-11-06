package eu.tealhelix.howibuy.v1.types;

/**
 * What a basket does to the user's Streak once purchased, judged on the scientific Overall Scores of its products.
 */
public enum StreakEffect {
	/**
	 * The basket extends the Streak by one.
	 */
	EXTEND,
	/**
	 * The basket resets the Streak to 0.
	 */
	RESET,
	/**
	 * The basket leaves the Streak as it is.
	 */
	NONE
}
