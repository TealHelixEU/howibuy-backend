package eu.tealhelix.howibuy.dao;

import java.util.Map;
import java.util.Set;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.v1.types.UserId;
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
	Uni<Map<ScoredSustainabilityDimension, Long>> retrieveProductCountsAbove(ReactivePersistenceContext em, UserId userId, double normalisedScore);

	/**
	 * @param em     The persistence context
	 * @param userId The user
	 * @return The badges the user holds, none for a user who holds none
	 */
	Uni<Set<Badge>> findBadges(ReactivePersistenceContext em, UserId userId);
}
