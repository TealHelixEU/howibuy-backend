package eu.tealhelix.betterme.services.generic.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.betterme.dao.UserProfileDao;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.services.generic.UserService;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.UserId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class UserServiceImpl implements UserService {
	private final UserProfileDao userProfileDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	public UserServiceImpl(UserProfileDao userProfileDao, ReactivePersistenceContextFactory persistenceContextFactory) {
		this.userProfileDao = userProfileDao;
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<User> requireUserFromValidIdmId(String userIdFromIdm, String name, boolean serviceFlag) {
		return persistenceContextFactory.withoutTransaction(em ->
				userProfileDao.requireByIdmId(em, userIdFromIdm, name, serviceFlag)
		);
	}

	@Override
	public Uni<User> requireUserWithId(UserId userId, String name, boolean serviceFlag) {
		return persistenceContextFactory.withoutTransaction(em ->
				userProfileDao.requireById(em, userId, name, serviceFlag)
		);
	}
}
