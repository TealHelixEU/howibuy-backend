package eu.tealhelix.howibuy.services.model;

import org.immutables.value.Value;

/**
 * Where a user stands with the Purchased Baskets they have bought: the XP earned in total and the Streak.
 */
@Value.Immutable
public interface XpAndStreak {
	/**
	 * The XP the user has earned in total with the Purchased Baskets.
	 */
	int getXpTotal();

	/**
	 * The user's Streak.
	 */
	int getStreak();
}
