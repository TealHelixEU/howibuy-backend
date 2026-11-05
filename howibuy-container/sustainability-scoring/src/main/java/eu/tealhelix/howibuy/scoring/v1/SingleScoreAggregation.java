package eu.tealhelix.howibuy.scoring.v1;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;

import java.util.EnumMap;
import java.util.Map;

import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;

/**
 * Reduces a product's measurements to one single score per dimension, by weighting each indicator and summing; health,
 * which has no indicators, takes the product's Nutri-Score points.
 * <p>
 * A single score is in its dimension's own units and points the natural way for an impact — more is worse — so
 * single scores of different dimensions are not comparable and are never shown. Normalising them is what makes them
 * so.
 */
final class SingleScoreAggregation {
	/**
	 * The single score of every dimension.
	 * <p>
	 * The scored corpus always passes {@link ScientificWeights#indicatorWeights()}. An indicator absent from the given
	 * weights contributes nothing.
	 */
	static Map<ScoredSustainabilityDimension, Double> aggregate(ProductIndicators product, Map<SustainabilityIndicator, Double> indicatorWeights) {
		var singleScores = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		for (var dimension : ScoredSustainabilityDimension.values()) {
			singleScores.put(dimension, dimension == HEALTH
					? product.nutriScorePoints()
					: singleScore(product, indicatorWeights, SustainabilityIndicator.of(dimension)));
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
