package eu.tealhelix.howibuy.scoring.v1;

import java.util.EnumMap;
import java.util.Map;

import eu.tealhelix.howibuy.v1.types.ImmutableWeightProfile;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.WeightProfile;

/**
 * Builds weight profiles small enough to compute the expected scores by hand: only the dimensions named carry weight,
 * every other is zero.
 */
final class WeightProfileTestUtils {
	static WeightProfile profile(Map<ScoredSustainabilityDimension, Double> dimensionWeights) {
		return ImmutableWeightProfile.builder()
				.dimensionWeights(withEveryDimension(dimensionWeights))
				.build();
	}

	private static Map<ScoredSustainabilityDimension, Double> withEveryDimension(Map<ScoredSustainabilityDimension, Double> given) {
		var complete = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		for (var dimension : ScoredSustainabilityDimension.values()) {
			complete.put(dimension, given.getOrDefault(dimension, 0.0));
		}
		return complete;
	}

	private WeightProfileTestUtils() {
		// NOOP
	}
}
