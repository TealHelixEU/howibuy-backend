package eu.tealhelix.betterme.dao;

import eu.tealhelix.betterme.v1.types.HasRetailerId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.v1.types.HasUserId;
import io.smallrye.mutiny.Uni;

/**
 * DAO interface for user consents.
 */
public interface ConsentDao {
	/**
	 * Check if the datastore contains the consent of the user with the given id to the retailer with the given id.
	 *
	 * @param em         The reactive persistence context
	 * @param userId     The id of the user to check if they have consented
	 * @param retailerId The id of the retailer to check for consent
	 * @return {@code true} if the consent check was successful
	 */
	Uni<Boolean> hasConsentedToRetailer(ReactivePersistenceContext em, HasUserId userId, HasRetailerId retailerId);

	/**
	 * Update the given users consent to the integration with the given retailer - {@code true} grants consent,
	 * {@code false} revokes.
	 *
	 * @param tx         The reactive persistence context
	 * @param userId     The id of the user to update their consent
	 * @param retailerId The id of the retailer to update consent
	 * @param consent    {@code true} grants consent, {@code false} revokes
	 * @return The previous consent value, if any
	 */
	Uni<Boolean> updateConsentToRetailer(ReactivePersistenceTxContext tx, HasUserId userId, HasRetailerId retailerId, boolean consent);
}
