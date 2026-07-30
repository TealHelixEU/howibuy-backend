package eu.tealhelix.howibuy.dao;

import eu.tealhelix.howibuy.v1.types.RetailerId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import io.smallrye.mutiny.Uni;

public interface RetailerDao {
	/**
	 * Read whether a retailer is allowed to act on behalf of users, distinguishing a retailer that is not allowed from
	 * one this application does not know at all.
	 *
	 * @param em         The persistence context
	 * @param retailerId The retailer
	 * @return The active flag of the retailer, or {@code null} if there is no retailer with this id
	 */
	Uni<Boolean> findActiveFlag(ReactivePersistenceContext em, RetailerId retailerId);
}
