package eu.tealhelix.howibuy.v1.types;

/**
 * How far a substitution is allowed to stray from the product the user scanned, by setting how strong the
 * substitutability between two categories must be to qualify. A larger level admits more distant categories.
 */
public enum SubstitutabilityLevel {
	SMALL,
	MEDIUM,
	LARGE
}
