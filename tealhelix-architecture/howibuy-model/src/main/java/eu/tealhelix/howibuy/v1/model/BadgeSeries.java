package eu.tealhelix.howibuy.v1.model;

import java.util.List;

import eu.tealhelix.howibuy.v1.types.Badge;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import org.immutables.value.Value;

/**
 * The badges of a {@link ScoredSustainabilityDimension}, with what the user has towards them.
 */
@Value.Immutable
public interface BadgeSeries {
	ScoredSustainabilityDimension getDimension();

	/**
	 * The user's purchased products above the threshold of the badges on the Normalised Score of the dimension, counted
	 * as the badges count them: every product of every Purchased Basket once per basket.
	 */
	long getProductCount();

	/**
	 * A level per badge of the dimension, in the order of {@link Badge}.
	 */
	List<BadgeLevel> getLevels();
}
