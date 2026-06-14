package eu.tealhelix.howibuy.dao.impl;

import java.util.Objects;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Root;

import eu.tealhelix.howibuy.dao.UserProfileDao;
import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity;
import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity_;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.types.Email;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class UserProfileDaoImpl implements UserProfileDao {
	@Override
	public Uni<User> createAutoUser(ReactivePersistenceTxContext tx) {
		var u = new UserProfileEntity();
		u.setId(UUID.randomUUID());
		return tx.persist(u).map(UserProfileUtils::toUser);
	}

	@Override
	public Uni<User> requireByIdmId(ReactivePersistenceContext em, String userIdFromIdm, String name, boolean serviceFlag) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(UserProfileEntity.class);
		Root<UserProfileEntity> userProfileEntity = q.from(UserProfileEntity.class);
		q.where(cb.equal(userProfileEntity.get(UserProfileEntity_.idmId), userIdFromIdm));
		return em.createQuery(q).getSingleResult().map(profile -> toUser(profile, name, serviceFlag))
				.onFailure(NotFoundException.class).transform(nfe -> new NotFoundException("No UserProfileEntity for IDM id " + userIdFromIdm, nfe));
	}

	@Override
	public Uni<User> requireById(ReactivePersistenceContext em, UserId userId, String name, boolean serviceFlag) {
		Objects.requireNonNull(userId);
		return em.find(UserProfileEntity.class, userId.asUuid()).map(profile -> toUser(profile, name, serviceFlag))
				.onItem().ifNull().failWith(() -> new NotFoundException(userId));
	}

	@Override
	public User toUser(UserId userId) {
		return new UserImpl(userId, null, null, false, false);
	}

	private User toUser(UserProfileEntity p, String name, boolean serviceFlag) {
		return new UserImpl(new UserIdImpl(p.getId().toString()), name, Email.of(p.getEmail()), false, serviceFlag);
	}
}
