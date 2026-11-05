package eu.tealhelix.howibuy.v1.model;

import eu.tealhelix.common.types.Nullable;
import org.immutables.value.Value;

/**
 * An Alternative: what to buy in place of the Reference Product.
 */
@Value.Immutable
public interface AlternativeForProduct {
	/**
	 * The Archetype Product drawn from the Pool of its degree of Substitutability.
	 */
	RecommendedArchetype getArchetype();

	/**
	 * The degree of Substitutability of the Archetype Product's category for the Reference Product's: 5, 4 or 3, the
	 * higher the more readily it stands in. An assessment offers one Alternative at each degree at most, the closest
	 * first.
	 */
	int getSubstitutability();

	/**
	 * The scientific Overall Score of the Archetype Product, which is always higher than the Reference Product's. The
	 * draw favours the user's own weights without requiring them to improve, so it is the scientific score that says how
	 * much better the Alternative is.
	 */
	double getOverallScore();

	/**
	 * The product of the user's Catalogue that stands for the Archetype Product.
	 */
	@Nullable
	ListedProduct getProduct();
}
