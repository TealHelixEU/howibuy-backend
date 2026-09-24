package eu.tealhelix.howibuy.dao;

import java.util.Optional;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.types.EmailAddress;
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
	 * Create the user profile of a user the IDM knows but this application has not seen yet.
	 *
	 * @param tx            The transaction
	 * @param userIdFromIdm The id of the user in the IDM
	 * @param name          The name of the user, as the IDM gives it
	 * @param email         The email of the user, as the IDM gives it, may be {@code null}
	 * @return The user object representing the newly created user profile; a failure with
	 *         {@code EntityAlreadyExistsException} if a profile for this IDM id already exists
	 */
	Uni<User> createFromIdm(ReactivePersistenceTxContext tx, String userIdFromIdm, String name, EmailAddress email);

	Uni<Optional<User>> findByIdmId(ReactivePersistenceContext em, String userIdFromIdm, String name);

	Uni<User> requireById(ReactivePersistenceContext em, UserId userId, String name, boolean serviceFlag);

	/**
	 * Create a simple, regular (non-system, non-service) user model holding information only about the user id, to be
	 * used in the impersonation from retailer scenario.
	 *
	 * @param userId The user id
	 * @return A user model for a regular user with the given id
	 */
	User toUser(UserId userId);
}
