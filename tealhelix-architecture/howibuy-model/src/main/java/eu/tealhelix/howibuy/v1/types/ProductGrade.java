package eu.tealhelix.howibuy.v1.types;

/**
 * The grade of one product of a basket, from its Overall Score under the Weighting Profile in force for the user. Not
 * the same thing as the {@link BasketGrade}, even though the letters coincide.
 */
public enum ProductGrade {
	/**
	 * A sustainable choice.
	 */
	A,
	/**
	 * An acceptable choice, with room for improvement.
	 */
	B,
	/**
	 * A poor choice.
	 */
	C
}
