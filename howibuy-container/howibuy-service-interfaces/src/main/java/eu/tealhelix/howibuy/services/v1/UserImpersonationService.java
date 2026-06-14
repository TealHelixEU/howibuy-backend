package eu.tealhelix.howibuy.services.v1;

import eu.tealhelix.common.v1.model.User;
import io.smallrye.mutiny.Uni;

public interface UserImpersonationService {
	/**
	 * Return a {@code User} object that represents a user to be impersonated by a retailer service account. Must check
	 * if the user has consented.
	 *
	 * @param currentUser   This is the <em>service account</em> representing a <em>retailer</em> trying to impersonate the user
	 * @param correlationId This is the correlation id in the retailer represented by the {@code currentUser} of the user that the service account is trying to impersonate
	 * @return The user object, filled with the appropriate data, {@code NotFoundException}, if the system cannot find the target user, {@code NotAuthorizedException}, if the user has not consented for the service to access their data
	 */
	Uni<User> impersonateUserAsRetailer(User currentUser, String correlationId);
}
