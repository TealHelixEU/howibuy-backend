package eu.tealhelix.howibuy.scoring.v1;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;

import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;

/**
 * Rescales a dimension's single scores against the whole corpus, turning quantities in the dimension's own units into
 * comparable scores in {@code [0, 1]} where higher means more sustainable.
 * <p>
 * Transcribed from the min-max normalization blocks of {@code TH_Algorithm_Implementation_v2026-05-20.Rmd},
 * including their asymmetry: the maximum is taken over non-outliers only, while the minimum is taken over everything.
 */
final class MinMaxNormalisation {
	/**
	 * The single score above which a product counts as an outlier, per dimension. Ignoring outliers when taking the
	 * maximum stops a handful of extreme products from compressing every other score into the top of the range.
	 * <p>
	 * These are absolute cut-offs from {@code upper_threshold} in the source script, calibrated against WP3's own
	 * weights. They stay valid only as long as the indicator weights do, which is why the indicator weights are the
	 * same for every user — see ADR 0005.
	 */
	private static final Map<ScoredSustainabilityDimension, Double> OUTLIER_THRESHOLDS = outlierThresholds();

	static OptionalDouble outlierThresholdFor(ScoredSustainabilityDimension dimension) {
		var threshold = OUTLIER_THRESHOLDS.get(dimension);
		return threshold == null ? OptionalDouble.empty() : OptionalDouble.of(threshold);
	}

	/**
	 * The normalised score of each of the given single scores, in the same order. A product whose single score exceeds
	 * the outlier threshold would normalise below zero, and is floored at zero — the worst score there is.
	 * <p>
	 * Where the reference implementation would divide by a zero range and propagate the resulting {@code NaN}, this
	 * gives every product the best score instead: if no product in the corpus is more impactful than any other, none of
	 * them is worse. The real corpus never takes that branch; small test corpora and single-product corpora do.
	 */
	static double[] normalize(double[] singleScores, OptionalDouble outlierThreshold) {
		if (singleScores.length == 0) return singleScores;

		var min = Double.POSITIVE_INFINITY;
		var max = Double.NEGATIVE_INFINITY;
		for (var score : singleScores) {
			min = Math.min(min, score);
			if (!isOutlier(score, outlierThreshold)) max = Math.max(max, score);
		}
		// Every product an outlier leaves no maximum to scale against; fall back to the whole corpus, outliers included.
		if (max == Double.NEGATIVE_INFINITY) return normalize(singleScores, OptionalDouble.empty());

		var range = max - min;
		if (range == 0.0) {
			var flat = new double[singleScores.length];
			Arrays.fill(flat, 1.0);
			return flat;
		}

		var normalised = new double[singleScores.length];
		for (var i = 0; i < singleScores.length; i++) {
			normalised[i] = Math.max(1.0 - (singleScores[i] - min) / range, 0.0);
		}
		return normalised;
	}

	private static boolean isOutlier(double singleScore, OptionalDouble outlierThreshold) {
		return outlierThreshold.isPresent() && singleScore > outlierThreshold.getAsDouble();
	}

	private static Map<ScoredSustainabilityDimension, Double> outlierThresholds() {
		var thresholds = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		thresholds.put(ENVIRONMENT, 4.0);
		thresholds.put(ANIMAL_WELFARE, 100.0);
		return thresholds;
	}

	private MinMaxNormalisation() {
		// NOOP
	}
}
