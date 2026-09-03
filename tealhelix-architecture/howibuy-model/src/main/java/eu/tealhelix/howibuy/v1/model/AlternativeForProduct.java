package eu.tealhelix.howibuy.v1.model;

import java.util.UUID;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.AlternativeForProductType;
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
	UUID getArchetypeProductId();

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
