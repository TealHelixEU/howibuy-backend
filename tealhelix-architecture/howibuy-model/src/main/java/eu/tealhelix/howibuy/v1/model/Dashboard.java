package eu.tealhelix.howibuy.v1.model;

import java.util.List;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import org.immutables.value.Value;

/**
 * Everything the dashboard of a user shows: where the user stands with the XP, the Streak and the Purchased Baskets, how
 * the purchases of a month compare with those of the month before, and the badges.
 */
@Value.Immutable
public interface Dashboard {
	/**
	 * The XP the user has earned in total with the Purchased Baskets.
	 */
	int getXpTotal();

	/**
	 * Of the other users with at least one Purchased Basket, the percentage whose XP total is below the user's, rounded
	 * down; a tie is not below. {@code null} when the user has no Purchased Basket, or no other user has one.
	 */
	@Nullable
	Integer getXpPercentile();

	/**
	 * The XP of every product that is not a Problematic Product.
	 */
	int getXpPerProduct();

	/**
	 * The user's Streak.
	 */
	int getStreak();

	/**
	 * The user's Purchased Baskets, those with nothing to assess included; 0 for a user who has bought none.
	 */
	long getPurchasedBasketCount();

	/**
	 * The averages of the month asked for and of the month before it, per {@link ScoredSustainabilityDimension}, in the
	 * order of the enum.
	 */
	List<DimensionProgress> getMonthlyProgress();

	/**
	 * The badges of every {@link ScoredSustainabilityDimension}, in the order of the enum.
	 */
	List<BadgeSeries> getBadgeSeries();
}
