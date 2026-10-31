package eu.tealhelix.howibuy.v1.types;

/**
 * The grade of a basket, based on the sustainability of the products in it. Not
 * the same thing as the {@link ProductGrade}, even though the letters coincide.
 */
public enum BasketGrade {
	/**
	 * The basket is good enough, no meaningful sustainability suggestion can be made. Candidate for a "golden streak".
	 */
	A,
	/**
	 * The basket has some issues, fewer than a threshold. Candidate for a "silver streak".
	 */
	B,
	/**
	 * The basket has many issues, strongly suggest the user to improve.
	 */
	C
}
