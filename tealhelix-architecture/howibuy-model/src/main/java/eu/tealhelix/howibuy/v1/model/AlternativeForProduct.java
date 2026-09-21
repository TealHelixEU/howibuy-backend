package eu.tealhelix.howibuy.v1.model;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.AlternativeForProductType;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import org.immutables.value.Value;

/**
 * Describes an alternative for a product.
 */
@Value.Immutable
public interface AlternativeForProduct {
	/**
	 * The type of the alternative. Every other field is populated unless the value is
	 * {@link AlternativeForProductType#NO_SUGGESTION}, which carries no product and no scores.
	 */
	AlternativeForProductType getType();

	@Nullable
	String getName();

	/**
	 * The archetype product being recommended — the same one the reference product was matched to, when the type is
	 * {@link AlternativeForProductType#GOOD_ENOUGH}.
	 */
	@Nullable
	ArchetypeProductId getArchetypeProductId();

	/**
	 * The SAFAD taxonomy path the recommended archetype sits in, from the top-level category down to the leaf it hangs
	 * from. A suggestion may well come from a different branch than the assessed product's own, since substitutability
	 * reaches across categories, so the path says what kind of food is being recommended.
	 */
	@Nullable
	String getL1Category();

	@Nullable
	String getL2Category();

	@Nullable
	String getL3Category();

	/**
	 * The overall score of the archetype the assessed product was matched to, and of the one recommended in its place.
	 * Both are measured under the criterion that chose this alternative — the user's own weights for the personal
	 * recommendation, WP3's for the scientific one, and the blend of the two for the combined one — so the pair
	 * explains why this product won that ranking, and the alternative never scores below the reference.
	 */
	@Nullable
	Double getReferenceOverallScore();

	@Nullable
	Double getAlternativeOverallScore();
}
