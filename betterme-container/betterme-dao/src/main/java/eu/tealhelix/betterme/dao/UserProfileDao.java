package eu.tealhelix.betterme.dao;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.UserId;
import io.smallrye.mutiny.Uni;

public interface UserProfileDao {
	/**
	 * Create a user profile for the automatic creation from a retailer scenario and return the resulting user object.
	 *
	 * @return The user object representing the newly created user profile
	 */
	Uni<User> createAutoUser(ReactivePersistenceTxContext tx);

	/**
	 * Create a simple, regular (non-system, non-service) user model holding information only about the user id, to be
	 * used in the impersonation from retailer scenario.
	 *
	 * @param userId The user id
	 * @return A user model for a regular user with the given id
	 */
	User toUser(UserId userId);
}
