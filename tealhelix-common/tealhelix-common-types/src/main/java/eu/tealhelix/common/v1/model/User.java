package eu.tealhelix.common.v1.model;

import java.security.Principal;

import eu.tealhelix.common.types.Email;
import eu.tealhelix.common.v1.types.HasUserId;

/**
 * A user of this application.
 */
public interface User extends Principal, HasUserId {
	Email getEmail();

	/**
	 * Check if this user is the system user.
	 *
	 * @return {@code true} if this user is the system user
	 */
	boolean isSystem();
}
