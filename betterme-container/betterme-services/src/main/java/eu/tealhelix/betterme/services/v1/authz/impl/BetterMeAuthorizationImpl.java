package eu.tealhelix.betterme.services.v1.authz.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.betterme.services.v1.authz.BetterMeAuthorization;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.v1.model.User;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class BetterMeAuthorizationImpl implements BetterMeAuthorization {
	@Override
	public Uni<Void> authorizeImpersonation(User currentUser) {
		return currentUser.isService() ? Uni.createFrom().voidItem() : Uni.createFrom().failure(new NotAuthorizedException());
	}
}
