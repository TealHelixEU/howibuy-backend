package eu.tealhelix.howibuy.scoring.v1;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIO_ECONOMIC;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.OptionalDouble;
import java.util.Set;

import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;

/**
 * Rescales one dimension's impacts against the products they were given for, turning quantities in the dimension's own
 * units into comparable scores in {@code [0, 1]} where higher means more sustainable.
 * <p>
 * Transcribed from {@code norm_one_dim} in {@code paper_analysis_2026_07_31_v15.qmd}, under the normalisation profile
 * {@code log_tails}: Environment and Health by its method {@code minmax_p}, Animal Welfare and Socio-economic by
 * {@code log_minmax_p}.
 */
final class PercentileNormalisation {
	/**
	 * The percentiles the scale is anchored at, {@code p_lo} and {@code p_hi} of {@code norm_one_dim}. Anchoring short of
	 * the extremes stops a handful of extreme products from compressing every other score into one end of the scale.
	 */
	private static final double LOWER_ANCHOR = 0.01;
	private static final double UPPER_ANCHOR = 0.99;

	/** The dimensions whose impacts are heavily skewed, which {@code log_tails} log-transforms before anchoring. */
	private static final Set<ScoredSustainabilityDimension> LOG_TRANSFORMED = EnumSet.of(ANIMAL_WELFARE, SOCIO_ECONOMIC);

	/**
	 * The normalised score of each of the given impacts of the given dimension, in the same order.
	 */
	static double[] normalise(ScoredSustainabilityDimension dimension, double[] impacts) {
		return LOG_TRANSFORMED.contains(dimension) ? normaliseLogTransformed(impacts) : normalise(impacts);
	}

	/**
	 * The method {@code minmax_p}: min–max between the 1st and the 99th percentile, clamped, so that an impact beyond an
	 * anchor scores as the anchor does, and inverted, so that the lowest impact scores best.
	 * <p>
	 * Where the two anchors coincide, the reference implementation divides by zero; this gives every product the best
	 * score instead: if no product is more impactful than another, none of them is worse. The real corpus never takes
	 * that branch; small test corpora do.
	 */
	static double[] normalise(double[] impacts) {
		var normalised = new double[impacts.length];
		if (impacts.length == 0) return normalised;

		var sorted = impacts.clone();
		Arrays.sort(sorted);
		double lower = quantile(sorted, LOWER_ANCHOR);
		double range = quantile(sorted, UPPER_ANCHOR) - lower;
		if (range == 0.0) {
			Arrays.fill(normalised, 1.0);
			return normalised;
		}

		for (int i = 0; i < impacts.length; i++) {
			normalised[i] = 1.0 - Math.min(Math.max((impacts[i] - lower) / range, 0.0), 1.0);
		}
		return normalised;
	}

	/**
	 * The method {@code log_minmax_p}: {@code log(max(x, 0) + m)} first, where {@code m} is the median of the positive
	 * impacts ({@code LOG_OFFSET_MODE = "relative_median"} at {@code LOG_OFFSET_VALUE = 1}), then {@link #normalise}.
	 * <p>
	 * With no positive impact there is no offset, and no need for one: every impact counts as zero, so every product
	 * gets the best score.
	 */
	static double[] normaliseLogTransformed(double[] impacts) {
		OptionalDouble offset = medianOfThePositive(impacts);
		if (offset.isEmpty()) {
			var flat = new double[impacts.length];
			Arrays.fill(flat, 1.0);
			return flat;
		}

		var transformed = new double[impacts.length];
		for (int i = 0; i < impacts.length; i++) {
			transformed[i] = Math.log(Math.max(impacts[i], 0.0) + offset.getAsDouble());
		}
		return normalise(transformed);
	}

	/**
	 * The quantile of the given values at the given probability as R takes it by default (type 7): linear
	 * interpolation between the two values nearest to position {@code (n - 1) × probability}.
	 *
	 * @param sorted      at least one value, in ascending order
	 * @param probability between 0 and 1
	 */
	static double quantile(double[] sorted, double probability) {
		double position = (sorted.length - 1) * probability;
		int below = (int) Math.floor(position);
		int above = (int) Math.ceil(position);
		double fraction = position - below;
		if (fraction == 0.0 || sorted[above] == sorted[below]) return sorted[below];
		else return (1.0 - fraction) * sorted[below] + fraction * sorted[above];
	}

	private static OptionalDouble medianOfThePositive(double[] impacts) {
		double[] positive = Arrays.stream(impacts).filter(impact -> impact > 0.0).sorted().toArray();
		if (positive.length == 0) return OptionalDouble.empty();

		int middle = positive.length / 2;
		if (positive.length % 2 == 1) return OptionalDouble.of(positive[middle]);
		else return OptionalDouble.of((positive[middle - 1] + positive[middle]) / 2.0);
	}

	private PercentileNormalisation() {
		// NOOP
	}
}
