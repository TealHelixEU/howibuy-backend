package eu.tealhelix.howibuy.dao.impl;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;

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
import eu.tealhelix.howibuy.services.model.ImmutableXpAndStreak;
import eu.tealhelix.howibuy.services.model.XpAndStreak;
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
				.map(profile -> toUser(profile, name));
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
		return em.createQuery(q).getSingleOptionalResult().map(profile -> profile.map(p -> toUser(p, name)));
	}

	@Override
	public Uni<User> requireById(ReactivePersistenceContext em, UserId userId, String name) {
		Objects.requireNonNull(userId);
		return em.find(UserProfileEntity.class, userId.asUuid())
				.onItem().ifNull().failWith(() -> new NotFoundException(userId))
				.map(profile -> toUser(profile, name));
	}

	/**
	 * Reads the columns themselves rather than the entity, which the session may hold from before an update.
	 */
	@Override
	public Uni<XpAndStreak> requireXpAndStreak(ReactivePersistenceContext em, UserId userId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var profile = q.from(UserProfileEntity.class);
		var xpTotal = profile.get(UserProfileEntity_.xpTotal);
		var streak = profile.get(UserProfileEntity_.streak);
		q.select(cb.tuple(xpTotal, streak));
		q.where(cb.equal(profile.get(UserProfileEntity_.id), userId.asUuid()));
		return em.createQuery(q).getSingleOptionalResult()
				.map(row -> row
						.map(tuple -> (XpAndStreak) ImmutableXpAndStreak.builder().xpTotal(tuple.get(xpTotal)).streak(tuple.get(streak)).build())
						.orElseThrow(() -> new NotFoundException(userId)));
	}

	@Override
	public Uni<Void> addXp(ReactivePersistenceTxContext tx, UserId userId, int xp) {
		return updateColumn(tx, userId, UserProfileEntity_.xpTotal, (cb, xpTotal) -> cb.sum(xpTotal, xp));
	}

	@Override
	public Uni<Void> increaseStreak(ReactivePersistenceTxContext tx, UserId userId) {
		return updateColumn(tx, userId, UserProfileEntity_.streak, (cb, streak) -> cb.sum(streak, 1));
	}

	@Override
	public Uni<Void> resetStreak(ReactivePersistenceTxContext tx, UserId userId) {
		return updateColumn(tx, userId, UserProfileEntity_.streak, (cb, _) -> cb.literal(0));
	}

	/**
	 * Set a column of the user's profile to a value computed from it, in one update of the database.
	 *
	 * @param newValue The new value of the column, from the criteria builder and the column's current value
	 * @return A {@code Uni} completing when the column has been set; a failure with {@code NotFoundException} if there is
	 *         no such user
	 */
	private static Uni<Void> updateColumn(
			ReactivePersistenceTxContext tx,
			UserId userId,
			SingularAttribute<UserProfileEntity, Integer> column,
			BiFunction<CriteriaBuilder, Expression<Integer>, Expression<Integer>> newValue
	) {
		var cb = tx.getCriteriaBuilder();
		var u = cb.createCriteriaUpdate(UserProfileEntity.class);
		var profile = u.from(UserProfileEntity.class);
		u.set(profile.get(column), newValue.apply(cb, profile.get(column)));
		u.where(cb.equal(profile.get(UserProfileEntity_.id), userId.asUuid()));
		return tx.createUpdate(u).execute().invoke(updated -> {
			if (updated == 0) throw new NotFoundException(userId);
		}).replaceWithVoid();
	}

	@Override
	public User toUser(UserId userId) {
		return new UserImpl(userId, null, null, false, true, Set.of());
	}

	private User toUser(UserProfileEntity p, String name) {
		return new UserImpl(new UserIdImpl(p.getId().toString()), name, EmailAddress.of(p.getEmail()), false, true, Set.of());
	}
}
