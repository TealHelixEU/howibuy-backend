package eu.tealhelix.howibuy.dao;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.v1.types.HasUserId;
import eu.tealhelix.howibuy.services.model.PurchasedBasket;
import eu.tealhelix.howibuy.v1.types.Badge;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import io.smallrye.mutiny.Uni;

public interface PurchasedBasketDao {
	/**
	 * Store a Purchased Basket with its products and the badges it earned. Each call stores another basket, even one
	 * equal to a basket already stored.
	 *
	 * @param tx     The persistence context
	 * @param basket The basket
	 * @return A {@code Uni} completing when the basket has been stored
	 */
	Uni<Void> store(ReactivePersistenceTxContext tx, PurchasedBasket basket);

	/**
	 * Count the stored products of the user whose Normalised Score is above a score, for every dimension. Every product
	 * of every Purchased Basket of the user counts once per basket.
	 *
	 * @param em              The persistence context
	 * @param userId          The user
	 * @param normalisedScore The score a product's Normalised Score of a dimension must be above to count in that dimension
	 * @return The number of products above the score, for every dimension; 0 for a user without stored products
	 */
	Uni<Map<ScoredSustainabilityDimension, Long>> retrieveProductCountsAbove(ReactivePersistenceContext em, HasUserId userId, double normalisedScore);

	/**
	 * @param em     The persistence context
	 * @param userId The user
	 * @return The badges the user holds, none for a user who holds none
	 */
	Uni<Set<Badge>> findBadges(ReactivePersistenceContext em, HasUserId userId);

	/**
	 * Average the Normalised Scores of the products the user bought in a month, for every dimension. The month runs by
	 * the time at the shop, from its first day at 00:00, inclusive, to the first day of the next month at 00:00,
	 * exclusive. Every product of every Purchased Basket counts once per basket.
	 *
	 * @param em     The persistence context
	 * @param userId The user
	 * @param month  The month
	 * @return The average Normalised Score of every dimension; empty when the user bought no scored product in the month
	 */
	Uni<Optional<Map<ScoredSustainabilityDimension, Double>>> retrieveAverageNormalisedScores(ReactivePersistenceContext em, HasUserId userId, YearMonth month);

	/**
	 * @param em     The persistence context
	 * @param userId The user
	 * @return How many Purchased Baskets the user has, those with nothing to assess included
	 */
	Uni<Long> countBaskets(ReactivePersistenceContext em, HasUserId userId);
}
