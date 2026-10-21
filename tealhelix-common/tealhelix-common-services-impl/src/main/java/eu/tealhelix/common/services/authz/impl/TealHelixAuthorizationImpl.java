package eu.tealhelix.common.services.authz.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.types.authorization.NotAuthenticatedException;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.v1.model.User;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class TealHelixAuthorizationImpl implements TealHelixAuthorization {

	public static final String REQUIRES_VALID_AUTHENTICATED_USER = "This operation requires a valid, authenticated user";

	@Override
	public void requireLogin(User user) {
		if (user == null || user.isUnauthenticated()) {
			throw new NotAuthenticatedException(REQUIRES_VALID_AUTHENTICATED_USER);
		}
	}

	@Override
	public void requirePerson(User user) {
		if (user == null || user.isUnauthenticated()) {
			throw new NotAuthenticatedException(REQUIRES_VALID_AUTHENTICATED_USER);
		} else if (!user.isPerson()) {
			throw new NotAuthorizedException("This operation is accessible only to users, not service accounts");
		}
	}

	@Override
	public void requireCatalogueSync(User user) {
		if (user == null || user.isUnauthenticated()) {
			throw new NotAuthenticatedException(REQUIRES_VALID_AUTHENTICATED_USER);
		} else if (!user.isCatalogueSync()) {
			throw new NotAuthorizedException("This operation is accessible only to the Catalogue Sync");
		}
	}

	@Override
	public void requireRetailer(User user) {
		if (user == null || user.isUnauthenticated()) {
			throw new NotAuthenticatedException(REQUIRES_VALID_AUTHENTICATED_USER);
		} else if (!user.isRetailer()) {
			throw new NotAuthorizedException("This operation is accessible only to Retailers");
		}
	}

	@Override
	public void requireOperator(User user) {
		if (user == null || user.isUnauthenticated()) {
			throw new NotAuthenticatedException(REQUIRES_VALID_AUTHENTICATED_USER);
		} else if (!user.isOperator()) {
			throw new NotAuthorizedException("This operation is accessible only to operators");
		}
	}

	@Override
	public Uni<Void> authorizeImpersonation(User currentUser) {
		return currentUser.isRetailer() ? Uni.createFrom().voidItem() : Uni.createFrom().failure(new NotAuthorizedException());
	}
}
