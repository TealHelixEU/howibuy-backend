package eu.tealhelix.howibuy.services.v1.authz;

import eu.tealhelix.common.v1.model.User;
import io.smallrye.mutiny.Uni;

/**
 * Module-specific methods for authorizing the access to the services of HowiBuy.
 */
public interface HowiBuyAuthorization {
	/**
	 * Ensure that the given user has impersonation rights, returning a failure with a {@code NotAuthorizedException}
	 * if they do not.
	 *
	 * @param currentUser The user to check for impersonation rights
	 * @return A successfule {@code Uni} if the user has the rights, a failure with a {@code NotAuthorizedException} otherwise
	 */
	Uni<Void> authorizeImpersonation(User currentUser);
}
