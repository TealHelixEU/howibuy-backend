package eu.tealhelix.common.v1.model;

import java.security.Principal;

import eu.tealhelix.common.types.Email;
import eu.tealhelix.common.v1.types.HasUserId;

/**
 * A user of this application.
 */
public interface User extends Principal, HasUserId {
	String SYSTEM_USER_ID = "00000000-0000-0000-0000-000000000000";
	String SYSTEM_USER_NAME = "system";

	Email getEmail();

	/**
	 * Check if this user is the system user.
	 *
	 * @return {@code true} if this user is the system user
	 */
	boolean isSystem();

	/**
	 * Check if this user represents a service account.
	 *
	 * @return {@code true} if this user represents a service account
	 */
	boolean isService();

	/**
	 * Check if this user is the unauthenticated (anonymous) user.
	 *
	 * @return {@code true} if this user is the unauthenticated (anonymous) user
	 */
	boolean isUnauthenticated();
}
