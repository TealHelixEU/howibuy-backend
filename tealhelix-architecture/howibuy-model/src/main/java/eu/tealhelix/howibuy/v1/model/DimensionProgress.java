package eu.tealhelix.howibuy.v1.model;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import org.immutables.value.Value;

/**
 * How the products a user bought in a month score in a {@link ScoredSustainabilityDimension}, against those of the month
 * before it. A month's average is the mean Normalised Score of the products of the user's Purchased Baskets bought in
 * it, by the time at the shop; every product counts once per basket.
 */
@Value.Immutable
public interface DimensionProgress {
	ScoredSustainabilityDimension getDimension();

	/**
	 * The average of the month, rounded to two decimals (half up); {@code null} when the user bought no scored product in
	 * it.
	 */
	@Nullable
	Double getCurrentAverage();

	/**
	 * The average of the month before, rounded to two decimals (half up); {@code null} when the user bought no scored
	 * product in it.
	 */
	@Nullable
	Double getPreviousAverage();

	/**
	 * The change from the previous average to the current one, in percent of the previous one, computed from the rounded
	 * averages and rounded to one decimal (half up); {@code null} when either average is {@code null}, or the previous one
	 * is 0.00.
	 */
	@Nullable
	Double getChangePercent();
}
