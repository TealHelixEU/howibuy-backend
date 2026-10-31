package eu.tealhelix.howibuy.dao;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.howibuy.services.model.PurchasedBasket;
import io.smallrye.mutiny.Uni;

public interface PurchasedBasketDao {
	/**
	 * Store a Purchased Basket with its products. Each call stores another basket, even one equal to a basket already
	 * stored.
	 *
	 * @param tx     The persistence context
	 * @param basket The basket
	 * @return A {@code Uni} completing when the basket has been stored
	 */
	Uni<Void> store(ReactivePersistenceTxContext tx, PurchasedBasket basket);
}
