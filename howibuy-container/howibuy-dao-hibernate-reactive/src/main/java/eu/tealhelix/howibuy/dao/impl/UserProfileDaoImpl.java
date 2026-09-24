package eu.tealhelix.howibuy.dao.impl;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Root;

import eu.tealhelix.common.dao.EntityAlreadyExistsException;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.types.EmailAddress;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.UserProfileDao;
import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity;
import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity_;
import io.smallrye.mutiny.Uni;
import org.hibernate.exception.ConstraintViolationException;

@ApplicationScoped
public class UserProfileDaoImpl implements UserProfileDao {
	private static final String UNIQUE_IDM_ID_CONSTRAINT = "UQ_TH_USER_PROFILE__IDM_ID";

	@Override
	public Uni<User> createAutoUser(ReactivePersistenceTxContext tx) {
		var u = new UserProfileEntity();
		u.setId(UUID.randomUUID());
		return tx.persist(u).map(UserProfileUtils::toUser);
	}

	@Override
	public Uni<User> createFromIdm(ReactivePersistenceTxContext tx, String userIdFromIdm, String name, EmailAddress email) {
		var u = new UserProfileEntity();
		u.setId(UUID.randomUUID());
		u.setIdmId(userIdFromIdm);
		u.setEmail(email != null ? email.asString() : null);
		return tx.persist(u)
				.flatMap(tx::flush)
				.onFailure(UserProfileDaoImpl::violatesUniqueIdmId)
				.transform(e -> new EntityAlreadyExistsException("A user profile already exists for IDM id: " + userIdFromIdm, e))
				.map(profile -> toUser(profile, name, false));
	}

	private static boolean violatesUniqueIdmId(Throwable e) {
		return e instanceof ConstraintViolationException cve && UNIQUE_IDM_ID_CONSTRAINT.equalsIgnoreCase(cve.getConstraintName());
	}

	@Override
	public Uni<Optional<User>> findByIdmId(ReactivePersistenceContext em, String userIdFromIdm, String name) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(UserProfileEntity.class);
		Root<UserProfileEntity> userProfileEntity = q.from(UserProfileEntity.class);
		q.where(cb.equal(userProfileEntity.get(UserProfileEntity_.idmId), userIdFromIdm));
		return em.createQuery(q).getSingleOptionalResult().map(profile -> profile.map(p -> toUser(p, name, false)));
	}

	@Override
	public Uni<User> requireById(ReactivePersistenceContext em, UserId userId, String name, boolean serviceFlag) {
		Objects.requireNonNull(userId);
		return em.find(UserProfileEntity.class, userId.asUuid())
				.onItem().ifNull().failWith(() -> new NotFoundException(userId))
				.map(profile -> toUser(profile, name, serviceFlag));
	}

	@Override
	public User toUser(UserId userId) {
		return new UserImpl(userId, null, null, false, false);
	}

	private User toUser(UserProfileEntity p, String name, boolean serviceFlag) {
		return new UserImpl(new UserIdImpl(p.getId().toString()), name, EmailAddress.of(p.getEmail()), false, serviceFlag);
	}
}
