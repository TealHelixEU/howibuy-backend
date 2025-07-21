package eu.tealhelix.betterme.dao.impl;

import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.betterme.dao.UserProfileDao;
import eu.tealhelix.betterme.dao.jpa.UserProfileEntity;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.UserId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class UserProfileDaoImpl implements UserProfileDao {
	@Override
	public Uni<User> createAutoUser(ReactivePersistenceTxContext tx) {
		var u = new UserProfileEntity();
		u.setId(UUID.randomUUID());
		u.setExternalId(UUID.randomUUID().toString());
		return tx.persist(u).map(UserProfileUtils::toUser);
	}

	@Override
	public User toUser(UserId userId) {
		return new UserImpl(userId, null, null, false, false);
	}
}
