package eu.tealhelix.howibuy.v1.types;

import java.util.EnumSet;
import java.util.Map;

import org.immutables.value.Value;

/**
 * The four weights, one per dimension, that combine an archetype product's normalised scores into a single overall
 * score.
 * <p>
 * Two profiles exist in production — the scientific one, which is WP3's and the same for everyone, and a personal
 * one derived from a user's completed compass attempt. The weights of the indicators within a dimension are no part of
 * a profile: they are WP3's, the same for every user, and belong to the scoring engine.
 */
@Value.Immutable
public interface WeightProfile {
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
