package eu.tealhelix.howibuy.services.v1.authz.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.types.authorization.NotAuthenticatedException;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.services.v1.authz.HowiBuyAuthorization;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class HowiBuyAuthorizationImpl implements HowiBuyAuthorization {

	public static final String REQUIRES_VALID_AUTHENTICATED_USER = "This operation requires a valid, authenticated user";

	@Override
	public void requireLogin(User user) {
		if (user == null || user.isUnauthenticated()) {
			throw new NotAuthenticatedException(REQUIRES_VALID_AUTHENTICATED_USER);
		}
	}

	@Override
	public void requireUserNotService(User user) {
		if (user == null || user.isUnauthenticated()) {
			throw new NotAuthenticatedException(REQUIRES_VALID_AUTHENTICATED_USER);
		} else if (user.isService()) {
			throw new NotAuthorizedException("This operation is accessible only to users, not service accounts");
		}
	}

	@Override
	public Uni<Void> authorizeImpersonation(User currentUser) {
		return currentUser.isService() ? Uni.createFrom().voidItem() : Uni.createFrom().failure(new NotAuthorizedException());
	}
}
