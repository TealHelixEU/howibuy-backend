package eu.tealhelix.howibuy.services.model;

import java.util.Map;
import java.util.UUID;

import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;
import org.immutables.value.Value;

/**
 * An archetype product with everything the sustainability scoring needs to place it against the rest of the corpus:
 * its measured indicator values, the Nutri-Score its health score is read from, and the L2 category at which
 * substitutability is decided.
 * <p>
 * The whole corpus is read as one, so the L2 category is resolved in that query rather than by walking the taxonomy
 * per product; a product's own category is the L3 leaf it hangs from, and its parent is what the substitutability
 * matrix speaks about.
 */
@Value.Immutable
public interface ArchetypeProductImpacts {
	UUID getId();

	/**
	 * The name shown to the user when this product is recommended as an alternative.
	 */
	String getName();

	/**
	 * The Agribalyse identifier: a stable external key, used to break ties between equally-scoring products.
	 */
	String getAgbCode();

	UUID getL2CategoryId();

	/**
	 * The measured value of each indicator. An indicator absent from the map contributes nothing to its dimension.
	 */
	Map<SustainabilityIndicator, Double> getIndicatorValues();

	/**
	 * The Nutri-Score label as WP3 writes it ({@code "Nutriscore_A"} to {@code "Nutriscore_E"}), or {@code "0"} for a
	 * product outside the scheme, which is how the data says a product has no health score and cannot be assessed.
	 */
	String getNutriScore();
}
