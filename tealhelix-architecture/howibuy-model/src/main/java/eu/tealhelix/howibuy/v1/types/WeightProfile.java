package eu.tealhelix.howibuy.v1.types;

import java.util.EnumSet;
import java.util.Map;

import org.immutables.value.Value;

/**
 * The weights that reduce an archetype product's measurements to a single overall score: one weight per indicator,
 * used to aggregate indicators into the single score of their dimension, and one weight per dimension, used to combine
 * the four normalised scores into the overall score.
 * <p>
 * Two profiles exist in production — the scientific one, which is WP3's and the same for everyone, and a personal
 * one derived from a user's completed compass attempt. They currently differ only in their dimension weights; the type
 * carries indicator weights as well because WP3's own reference implementation varies them, and reproducing its output
 * exactly requires being able to say so.
 */
@Value.Immutable
public interface WeightProfile {
	/**
	 * The weight of each indicator within the single score of its own dimension. An indicator absent from the map
	 * contributes nothing.
	 */
	Map<SustainabilityIndicator, Double> getIndicatorWeights();

	/**
	 * The weight of each of the four dimensions in the overall score. All four are required and they must sum to 1.
	 */
	Map<ScoredSustainabilityDimension, Double> getDimensionWeights();

	@Value.Check
	default void checkDimensionWeights() {
		var missing = EnumSet.allOf(ScoredSustainabilityDimension.class);
		missing.removeAll(getDimensionWeights().keySet());
		if (!missing.isEmpty()) {
			throw new IllegalStateException("Weight profile is missing dimensions: " + missing);
		}
		var sum = getDimensionWeights().values().stream().mapToDouble(Double::doubleValue).sum();
		if (Math.abs(sum - 1.0) > DIMENSION_WEIGHT_TOLERANCE) {
			throw new IllegalStateException("Dimension weights of a weight profile must sum to 1, sum: " + sum);
		}
	}

	/**
	 * How far the dimension weights may sum away from 1 before the profile is rejected. Loose enough to accept weights
	 * written out to a few decimals, as WP3 writes them.
	 */
	double DIMENSION_WEIGHT_TOLERANCE = 1e-6;
}
