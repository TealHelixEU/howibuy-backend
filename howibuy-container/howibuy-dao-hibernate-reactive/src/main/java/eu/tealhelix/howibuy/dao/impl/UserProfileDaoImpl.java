package eu.tealhelix.howibuy.dao.impl;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

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
import eu.tealhelix.common.v1.types.HasUserId;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.UserProfileDao;
import eu.tealhelix.howibuy.dao.jpa.PurchasedBasketEntity;
import eu.tealhelix.howibuy.dao.jpa.PurchasedBasketEntity_;
import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity;
import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity_;
import eu.tealhelix.howibuy.services.model.ImmutableXpAndStreak;
import eu.tealhelix.howibuy.services.model.ImmutableXpStanding;
import eu.tealhelix.howibuy.services.model.XpAndStreak;
import eu.tealhelix.howibuy.services.model.XpStanding;
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
	public Uni<User> requireById(ReactivePersistenceContext em, HasUserId userId, String name) {
		Objects.requireNonNull(userId);
		return em.find(UserProfileEntity.class, userId.getId().asUuid())
				.onItem().ifNull().failWith(() -> new NotFoundException(userId.getId()))
				.map(profile -> toUser(profile, name));
	}

	/**
	 * Reads the columns themselves rather than the entity, which the session may hold from before an update.
	 */
	@Override
	public Uni<XpAndStreak> requireXpAndStreak(ReactivePersistenceContext em, HasUserId userId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var profile = q.from(UserProfileEntity.class);
		var xpTotal = profile.get(UserProfileEntity_.xpTotal);
		var streak = profile.get(UserProfileEntity_.streak);
		q.select(cb.tuple(xpTotal, streak));
		q.where(cb.equal(profile.get(UserProfileEntity_.id), userId.getId().asUuid()));
		return em.createQuery(q).getSingleOptionalResult()
				.map(row -> row
						.map(tuple -> (XpAndStreak) ImmutableXpAndStreak.builder().xpTotal(tuple.get(xpTotal)).streak(tuple.get(streak)).build())
						.orElseThrow(() -> new NotFoundException(userId.getId())));
	}

	/**
	 * The user and the other users are two roots over the profiles. The other users who have a basket are picked by a
	 * sub-query of the users of the baskets, so each counts once, however many baskets they have. One behind counts
	 * through a {@code CASE} without an {@code ELSE}, as the products above a score in {@link PurchasedBasketDaoImpl}: their
	 * id when their XP total is lower, otherwise {@code NULL}, which {@code COUNT} skips. Without the user, the user's root
	 * has no row, and nothing is counted.
	 */
	@Override
	public Uni<XpStanding> retrieveXpStanding(ReactivePersistenceContext em, HasUserId userId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var user = q.from(UserProfileEntity.class);
		var other = q.from(UserProfileEntity.class);
		var otherId = other.get(UserProfileEntity_.id);
		var shoppers = q.subquery(UUID.class);
		var basket = shoppers.from(PurchasedBasketEntity.class);
		shoppers.select(basket.get(PurchasedBasketEntity_.userProfileId));
		var otherShoppers = cb.count(otherId);
		var behind = cb.count(cb.<UUID>selectCase().when(cb.lt(other.get(UserProfileEntity_.xpTotal), user.get(UserProfileEntity_.xpTotal)), otherId));
		q.select(cb.tuple(otherShoppers, behind));
		q.where(
				cb.equal(user.get(UserProfileEntity_.id), userId.getId().asUuid()),
				cb.notEqual(otherId, userId.getId().asUuid()),
				otherId.in(shoppers));
		return em.createQuery(q).getSingleResult()
				.map(row -> (XpStanding) ImmutableXpStanding.builder().otherShoppers(row.get(otherShoppers)).behind(row.get(behind)).build());
	}

	@Override
	public Uni<Void> addXp(ReactivePersistenceTxContext tx, HasUserId userId, int xp) {
		return updateColumn(tx, userId, UserProfileEntity_.xpTotal, (cb, xpTotal) -> cb.sum(xpTotal, xp));
	}

	@Override
	public Uni<Void> increaseStreak(ReactivePersistenceTxContext tx, HasUserId userId) {
		return updateColumn(tx, userId, UserProfileEntity_.streak, (cb, streak) -> cb.sum(streak, 1));
	}

	@Override
	public Uni<Void> resetStreak(ReactivePersistenceTxContext tx, HasUserId userId) {
		return updateColumn(tx, userId, UserProfileEntity_.streak, (cb, _) -> cb.literal(0));
	}

	@Override
	public Uni<Void> lock(ReactivePersistenceTxContext tx, HasUserId userId) {
		return tx.find(UserProfileEntity.class, userId.getId().asUuid(), PESSIMISTIC_WRITE)
				.onItem().ifNull().failWith(() -> new NotFoundException(userId.getId()))
				.replaceWithVoid();
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
			HasUserId userId,
			SingularAttribute<UserProfileEntity, Integer> column,
			BiFunction<CriteriaBuilder, Expression<Integer>, Expression<Integer>> newValue
	) {
		var cb = tx.getCriteriaBuilder();
		var u = cb.createCriteriaUpdate(UserProfileEntity.class);
		var profile = u.from(UserProfileEntity.class);
		u.set(profile.get(column), newValue.apply(cb, profile.get(column)));
		u.where(cb.equal(profile.get(UserProfileEntity_.id), userId.getId().asUuid()));
		return tx.createUpdate(u).execute().invoke(updated -> {
			if (updated == 0) throw new NotFoundException(userId.getId());
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
