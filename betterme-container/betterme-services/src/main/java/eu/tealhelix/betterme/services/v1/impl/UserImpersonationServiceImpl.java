package eu.tealhelix.betterme.services.v1.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.betterme.services.v1.UserImpersonationService;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class UserImpersonationServiceImpl implements UserImpersonationService {
	@Override
	public Uni<User> impersonateUser(User currentUser, String targetUserId) {
		// TODO Check access rights of currentUser
		// TODO Check if the target user exists and has consented
		var userId = new UserIdImpl(targetUserId);
		return Uni.createFrom().item(new UserImpl(userId, null, null, false, false));
	}
}
