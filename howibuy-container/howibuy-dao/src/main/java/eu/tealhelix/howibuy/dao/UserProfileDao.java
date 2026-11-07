package eu.tealhelix.howibuy.dao;

import java.util.Optional;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.types.EmailAddress;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.HasUserId;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.howibuy.services.model.XpAndStreak;
import eu.tealhelix.howibuy.services.model.XpStanding;
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

	Uni<User> requireById(ReactivePersistenceContext em, HasUserId userId, String name);

	/**
	 * @param em     The persistence context
	 * @param userId The user
	 * @return The XP the user has earned in total and their Streak; a failure with {@code NotFoundException} if there is
	 *         no such user
	 */
	Uni<XpAndStreak> requireXpAndStreak(ReactivePersistenceContext em, HasUserId userId);

	/**
	 * @param em     The persistence context
	 * @param userId The user
	 * @return How many other users have at least one Purchased Basket, and how many of them have a lower XP total than
	 *         the user; none of either if there is no such user
	 */
	Uni<XpStanding> retrieveXpStanding(ReactivePersistenceContext em, HasUserId userId);

	/**
	 * Add to the XP the user has earned in total, in one update of the database, so that additions made at the same time
	 * are all counted.
	 *
	 * @param tx     The transaction
	 * @param userId The user
	 * @param xp     The XP to add
	 * @return A {@code Uni} completing when the XP has been added; a failure with {@code NotFoundException} if there is
	 *         no such user
	 */
	Uni<Void> addXp(ReactivePersistenceTxContext tx, HasUserId userId, int xp);

	/**
	 * Increase the user's Streak by one, in one update of the database, so that increases made at the same time are all
	 * counted.
	 *
	 * @param tx     The transaction
	 * @param userId The user
	 * @return A {@code Uni} completing when the Streak has been increased; a failure with {@code NotFoundException} if
	 *         there is no such user
	 */
	Uni<Void> increaseStreak(ReactivePersistenceTxContext tx, HasUserId userId);

	/**
	 * Set the user's Streak to 0.
	 *
	 * @param tx     The transaction
	 * @param userId The user
	 * @return A {@code Uni} completing when the Streak has been reset; a failure with {@code NotFoundException} if there
	 *         is no such user
	 */
	Uni<Void> resetStreak(ReactivePersistenceTxContext tx, HasUserId userId);

	/**
	 * Lock the user's profile until the transaction ends, so that another transaction locking it waits for this one.
	 *
	 * @param tx     The transaction
	 * @param userId The user
	 * @return A {@code Uni} completing when the profile is locked; a failure with {@code NotFoundException} if there is no
	 *         such user
	 */
	Uni<Void> lock(ReactivePersistenceTxContext tx, HasUserId userId);

	/**
	 * Create a simple, regular (non-system, non-service) user model holding information only about the user id, to be
	 * used in the impersonation from retailer scenario.
	 *
	 * @param userId The user id
	 * @return A user model for a regular user with the given id
	 */
	User toUser(UserId userId);
}
