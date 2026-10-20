package eu.tealhelix.common.services.authz;

import eu.tealhelix.common.v1.model.User;
import io.smallrye.mutiny.Uni;

/**
 * Methods for authorizing access to the services of TealHelix.
 */
public interface TealHelixAuthorization {
	void requireLogin(User user);

	/**
	 * Ensure that the given user is an authenticated person, not a service account.
	 *
	 * @param user The user to check
	 * @throws eu.tealhelix.common.types.authorization.NotAuthenticatedException If the user is not authenticated
	 * @throws eu.tealhelix.common.types.authorization.NotAuthorizedException    If the user is a service account
	 */
	void requirePerson(User user);

	/**
	 * Ensure that the given user is the Catalogue Sync, which carries the Product Changes between the services.
	 *
	 * @param user The user to check
	 * @throws eu.tealhelix.common.types.authorization.NotAuthenticatedException If the user is not authenticated
	 * @throws eu.tealhelix.common.types.authorization.NotAuthorizedException    If the user does not have the
	 *                                                                            {@code CATALOGUE_SYNC} role
	 */
	void requireCatalogueSync(User user);

	/**
	 * Ensure that the given user is a Retailer.
	 *
	 * @param user The user to check
	 * @throws eu.tealhelix.common.types.authorization.NotAuthenticatedException If the user is not authenticated
	 * @throws eu.tealhelix.common.types.authorization.NotAuthorizedException    If the user does not have the
	 *                                                                            {@code RETAILER} role
	 */
	void requireRetailer(User user);

	/**
	 * Ensure that the given user has impersonation rights, returning a failure with a {@code NotAuthorizedException}
	 * if they do not. Retailers have them.
	 *
	 * @param currentUser The user to check for impersonation rights
	 * @return A successfule {@code Uni} if the user has the rights, a failure with a {@code NotAuthorizedException} otherwise
	 */
	Uni<Void> authorizeImpersonation(User currentUser);
}
