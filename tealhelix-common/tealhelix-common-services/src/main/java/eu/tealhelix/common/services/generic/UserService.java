package eu.tealhelix.common.services.generic;

import eu.tealhelix.common.types.EmailAddress;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.UserId;
import io.smallrye.mutiny.Uni;

public interface UserService {
	/**
	 * Find or create a user of this application given a valid, verified IDM id. Anyone with an account in the IDM may
	 * use the application, so a user presenting a valid IDM token for the first time gets a profile then and there.
	 *
	 * @param userIdFromIdm The id of the user in the IDM, from a token already validated
	 * @param name          The name of the user, as the IDM gives it
	 * @param email         The email of the user, as the IDM gives it, may be {@code null}; recorded only when the
	 *                      profile is created
	 * @return The user, asynchronously
	 */
	Uni<User> findOrCreateUserFromValidIdmId(String userIdFromIdm, String name, EmailAddress email);

	Uni<User> requireUserWithId(UserId userId, String name, boolean serviceFlag);
}
