package eu.tealhelix.howibuy.scoring.v1;

import java.util.EnumMap;
import java.util.Map;

import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;

/**
 * Reduces a product's measurements to one single score per dimension, by weighting each indicator and summing.
 * <p>
 * A single score is in its dimension's own units and points the natural way for an impact — more is worse — so
 * single scores of different dimensions are not comparable and are never shown. Normalising them is what makes them
 * so.
 */
final class SingleScoreAggregation {
	/**
	 * The single score of every dimension that has indicators, which is all of them except
	 * {@link ScoredSustainabilityDimension#HEALTH}.
	 * <p>
	 * Takes the indicator weights rather than the whole weighting profile: the dimension weights play no part in a
	 * single score, and saying so in the signature is what lets the scored corpus be cached on the indicator weights
	 * alone.
	 */
	static Map<ScoredSustainabilityDimension, Double> aggregate(ProductIndicators product, Map<SustainabilityIndicator, Double> indicatorWeights) {
		var singleScores = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		for (var dimension : ScoredSustainabilityDimension.values()) {
			var indicators = SustainabilityIndicator.of(dimension);
			if (!indicators.isEmpty()) singleScores.put(dimension, singleScore(product, indicatorWeights, indicators));
		}
		return singleScores;
	}

	private static double singleScore(ProductIndicators product, Map<SustainabilityIndicator, Double> indicatorWeights, Iterable<SustainabilityIndicator> indicators) {
		double singleScore = 0.0;
		for (var indicator : indicators) {
			var weight = indicatorWeights.getOrDefault(indicator, 0.0);
			var value = product.values().getOrDefault(indicator, 0.0);
			singleScore += weight * PefNormalization.weightScaleFor(indicator) * value;
		}
		return singleScore;
	}

	private SingleScoreAggregation() {
		// NOOP
	}
}
