package eu.tealhelix.common.v1.model;

import java.security.Principal;
import java.util.Set;

import eu.tealhelix.common.types.EmailAddress;
import eu.tealhelix.common.v1.types.HasUserId;

/**
 * A user of this application.
 */
public interface User extends Principal, HasUserId {
	String SYSTEM_USER_ID = "00000000-0000-0000-0000-000000000000";
	String SYSTEM_USER_NAME = "system";

	EmailAddress getEmail();

	/**
	 * Check if this user is the system user.
	 *
	 * @return {@code true} if this user is the system user
	 */
	boolean isSystem();

	/**
	 * Check if this user is a person, as opposed to a service account.
	 *
	 * @return {@code true} if this user is a person
	 */
	boolean isPerson();

	/**
	 * The roles of this user, which tell what it may do.
	 *
	 * @return The roles, empty if it has none
	 */
	Set<Role> getRoles();

	default boolean isRetailer() {
		return getRoles().contains(Role.RETAILER);
	}

	default boolean isCatalogueSync() {
		return getRoles().contains(Role.CATALOGUE_SYNC);
	}

	default boolean isOperator() {
		return getRoles().contains(Role.OPERATOR);
	}

	/**
	 * Check if this user is the unauthenticated (anonymous) user.
	 *
	 * @return {@code true} if this user is the unauthenticated (anonymous) user
	 */
	boolean isUnauthenticated();
}
