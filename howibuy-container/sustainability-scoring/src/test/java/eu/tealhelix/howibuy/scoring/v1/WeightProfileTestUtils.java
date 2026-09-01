package eu.tealhelix.howibuy.scoring.v1;

import java.util.EnumMap;
import java.util.Map;

import eu.tealhelix.howibuy.v1.types.ImmutableWeightProfile;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;
import eu.tealhelix.howibuy.v1.types.WeightProfile;

/**
 * Builds weight profiles small enough to compute the expected scores by hand: only the indicators and dimensions named
 * carry weight, everything else is zero.
 */
final class WeightProfileTestUtils {
	static WeightProfile profile(Map<SustainabilityIndicator, Double> indicatorWeights, Map<ScoredSustainabilityDimension, Double> dimensionWeights) {
		return ImmutableWeightProfile.builder()
				.indicatorWeights(indicatorWeights)
				.dimensionWeights(withEveryDimension(dimensionWeights))
				.build();
	}

	/**
	 * A profile that weights indicators but does not care how the dimensions combine, for tests that only look at
	 * single or normalised scores. The dimension weights are the scientific quarters, which are valid and irrelevant.
	 */
	static WeightProfile indicatorsOnly(Map<SustainabilityIndicator, Double> indicatorWeights) {
		return profile(indicatorWeights, Map.of());
	}

	private static Map<ScoredSustainabilityDimension, Double> withEveryDimension(Map<ScoredSustainabilityDimension, Double> given) {
		if (given.isEmpty()) return evenDimensionWeights();
		var complete = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		for (var dimension : ScoredSustainabilityDimension.values()) {
			complete.put(dimension, given.getOrDefault(dimension, 0.0));
		}
		return complete;
	}

	private static Map<ScoredSustainabilityDimension, Double> evenDimensionWeights() {
		var weights = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		double uniformWeight = 1.0 / ScoredSustainabilityDimension.values().length;
		for (var dimension : ScoredSustainabilityDimension.values()) {
			weights.put(dimension, uniformWeight);
		}
		return weights;
	}

	private WeightProfileTestUtils() {
		// NOOP
	}
}
