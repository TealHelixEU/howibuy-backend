package eu.tealhelix.howibuy.services.model;

import org.immutables.value.Value;

/**
 * Where a user's XP total stands among the other users who have bought at least one Purchased Basket.
 */
@Value.Immutable
public interface XpStanding {
	/**
	 * How many other users have at least one Purchased Basket.
	 */
	long getOtherShoppers();

	/**
	 * How many of the other users with at least one Purchased Basket have a lower XP total than the user.
	 */
	long getBehind();
}
