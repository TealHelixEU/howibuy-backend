package eu.tealhelix.howibuy.dao.impl;

import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity;
import eu.tealhelix.common.types.impl.EmailImpl;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;

interface UserProfileUtils {
	/**
	 * Convert a user profile entity to the corresponding user model for regular (non-system, non-service) users.
	 *
	 * @param u The user profile to convert
	 * @return The corresponding {@code User} model
	 */
	static User toUser(UserProfileEntity u) {
		return new UserImpl(new UserIdImpl(u.getId().toString()), u.getEmail(), new EmailImpl(u.getEmail()), false, false);
	}

	/**
	 * Create a simple, regular (non-system, non-service) user model holding information only about the user id, to be
	 * used in the impersonation from retailer scenario.
	 *
	 * @param userId The user id
	 * @return A user model for a regular user with the given id
	 */
	static User toUser(UserId userId) {
		return new UserImpl(userId, null, null, false, false);
	}
}
