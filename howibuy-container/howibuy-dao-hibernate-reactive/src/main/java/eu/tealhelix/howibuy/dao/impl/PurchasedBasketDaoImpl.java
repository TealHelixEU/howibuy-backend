package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIO_ECONOMIC;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.SingularAttribute;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.v1.types.HasUserId;
import eu.tealhelix.howibuy.dao.PurchasedBasketDao;
import eu.tealhelix.howibuy.dao.jpa.PurchasedBasketEntity;
import eu.tealhelix.howibuy.dao.jpa.PurchasedBasketEntity_;
import eu.tealhelix.howibuy.dao.jpa.PurchasedBasketProductEntity;
import eu.tealhelix.howibuy.dao.jpa.PurchasedBasketProductEntity_;
import eu.tealhelix.howibuy.dao.jpa.UserBadgeEntity;
import eu.tealhelix.howibuy.dao.jpa.UserBadgeEntity_;
import eu.tealhelix.howibuy.services.model.PurchasedBasket;
import eu.tealhelix.howibuy.services.model.PurchasedBasketProduct;
import eu.tealhelix.howibuy.v1.types.Badge;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class PurchasedBasketDaoImpl implements PurchasedBasketDao {
	/**
	 * The basket, its products and its badges are handed to the session in one call: a chain of a call per product would
	 * nest as deep as the products are many, and overflow the stack of the event loop.
	 */
	@Override
	public Uni<Void> store(ReactivePersistenceTxContext tx, PurchasedBasket basket) {
		var basketEntity = toEntity(basket);
		var entities = new ArrayList<>();
		entities.add(basketEntity);
		for (var product : basket.getProducts()) {
			entities.add(toEntity(basketEntity.getId(), product));
		}
		for (var badge : basket.getEarnedBadges()) {
			entities.add(toEntity(basketEntity, badge));
		}
		return tx.persistAll(entities.toArray());
	}

	/**
	 * A product counts in a dimension through a {@code CASE} without an {@code ELSE}: the id of its basket, never
	 * {@code NULL}, when the score is above, otherwise {@code NULL}, which {@code COUNT} skips. A constant in place of the
	 * id would be sent as a parameter of no type, which Postgres takes for text. The baskets of the user are picked by a
	 * sub-query, since the entities are not associated.
	 */
	@Override
	public Uni<Map<ScoredSustainabilityDimension, Long>> retrieveProductCountsAbove(ReactivePersistenceContext em, HasUserId userId, double normalisedScore) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var product = q.from(PurchasedBasketProductEntity.class);
		var basketId = product.get(PurchasedBasketProductEntity_.purchasedBasketId);
		var counts = new EnumMap<ScoredSustainabilityDimension, Expression<Long>>(ScoredSustainabilityDimension.class);
		scoreColumns().forEach((dimension, column) -> counts.put(dimension, cb.count(cb.<UUID>selectCase().when(cb.gt(product.get(column), normalisedScore), basketId))));
		var basketsOfTheUser = q.subquery(UUID.class);
		var basket = basketsOfTheUser.from(PurchasedBasketEntity.class);
		basketsOfTheUser.select(basket.get(PurchasedBasketEntity_.id)).where(cb.equal(basket.get(PurchasedBasketEntity_.userProfileId), userId.getId().asUuid()));
		q.select(cb.tuple(counts.values().toArray(Selection<?>[]::new)));
		q.where(basketId.in(basketsOfTheUser));
		return em.createQuery(q).getSingleResult().map(row -> perDimension(row, counts));
	}

	@Override
	public Uni<Set<Badge>> findBadges(ReactivePersistenceContext em, HasUserId userId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(Badge.class);
		var userBadge = q.from(UserBadgeEntity.class);
		q.select(userBadge.get(UserBadgeEntity_.badge));
		q.where(cb.equal(userBadge.get(UserBadgeEntity_.userProfileId), userId.getId().asUuid()));
		return em.createQuery(q).getResultList().map(Set::copyOf);
	}

	/**
	 * The baskets of the month are picked by a sub-query, by {@code purchased_at}, the time at the shop. Without a scored
	 * product the averages are {@code NULL}; a stored product has every score.
	 */
	@Override
	public Uni<Optional<Map<ScoredSustainabilityDimension, Double>>> retrieveAverageNormalisedScores(ReactivePersistenceContext em, HasUserId userId, YearMonth month) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var product = q.from(PurchasedBasketProductEntity.class);
		var averages = new EnumMap<ScoredSustainabilityDimension, Expression<Double>>(ScoredSustainabilityDimension.class);
		scoreColumns().forEach((dimension, column) -> averages.put(dimension, cb.avg(product.get(column))));
		var basketsOfTheMonth = q.subquery(UUID.class);
		var basket = basketsOfTheMonth.from(PurchasedBasketEntity.class);
		var purchasedAt = basket.get(PurchasedBasketEntity_.purchasedAt);
		basketsOfTheMonth.select(basket.get(PurchasedBasketEntity_.id)).where(
				cb.equal(basket.get(PurchasedBasketEntity_.userProfileId), userId.getId().asUuid()),
				cb.greaterThanOrEqualTo(purchasedAt, month.atDay(1).atStartOfDay()),
				cb.lessThan(purchasedAt, month.plusMonths(1).atDay(1).atStartOfDay()));
		q.select(cb.tuple(averages.values().toArray(Selection<?>[]::new)));
		q.where(product.get(PurchasedBasketProductEntity_.purchasedBasketId).in(basketsOfTheMonth));
		return em.createQuery(q).getSingleResult().map(row -> {
			var result = perDimension(row, averages);
			return result.containsValue(null) ? Optional.empty() : Optional.of(result);
		});
	}

	@Override
	public Uni<Long> countBaskets(ReactivePersistenceContext em, HasUserId userId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(Long.class);
		var basket = q.from(PurchasedBasketEntity.class);
		q.select(cb.count(basket));
		q.where(cb.equal(basket.get(PurchasedBasketEntity_.userProfileId), userId.getId().asUuid()));
		return em.createQuery(q).getSingleResult();
	}

	/**
	 * The value of every dimension in a row, read from the expression selected for it.
	 */
	private static <T> Map<ScoredSustainabilityDimension, T> perDimension(Tuple row, Map<ScoredSustainabilityDimension, Expression<T>> selected) {
		var result = new EnumMap<ScoredSustainabilityDimension, T>(ScoredSustainabilityDimension.class);
		selected.forEach((dimension, expression) -> result.put(dimension, row.get(expression)));
		return result;
	}

	/**
	 * The column of the Normalised Score of every dimension; built on every call, since the metamodel is filled in only
	 * when the persistence unit starts.
	 */
	private static Map<ScoredSustainabilityDimension, SingularAttribute<PurchasedBasketProductEntity, Double>> scoreColumns() {
		return Map.of(
				ENVIRONMENT, PurchasedBasketProductEntity_.scoreEnvironment,
				ANIMAL_WELFARE, PurchasedBasketProductEntity_.scoreAnimalWelfare,
				SOCIO_ECONOMIC, PurchasedBasketProductEntity_.scoreSocioEconomic,
				HEALTH, PurchasedBasketProductEntity_.scoreHealth);
	}

	private static PurchasedBasketEntity toEntity(PurchasedBasket basket) {
		var entity = new PurchasedBasketEntity();
		entity.setId(UUID.randomUUID());
		entity.setUserProfileId(basket.getUserId().asUuid());
		entity.setRetailerId(basket.getRetailerId().asUuid());
		entity.setCatalogueId(basket.getCatalogueId().asString());
		entity.setPurchasedAt(basket.getPurchasedAt().toLocalDateTime());
		entity.setPurchasedAtOffset(basket.getPurchasedAt().getOffset().getTotalSeconds());
		entity.setRecordedAt(basket.getRecordedAt());
		entity.setOutcomeType(basket.getType());
		entity.setBasketMatch(basket.getBasketMatch());
		entity.setXpValue(basket.getXpValue());
		entity.setCompassAttemptId(basket.getCompassAttemptId());
		return entity;
	}

	private static PurchasedBasketProductEntity toEntity(UUID basketId, PurchasedBasketProduct product) {
		var entity = new PurchasedBasketProductEntity();
		entity.setPurchasedBasketId(basketId);
		entity.setProductKey(product.getProductKey().asString());
		entity.setArchetypeProductId(product.getArchetypeProductId().asUuid());
		entity.setPersonalOverallScore(product.getPersonalOverallScore());
		entity.setScientificOverallScore(product.getScientificOverallScore());
		entity.setScoreEnvironment(product.getNormalisedScores().get(ENVIRONMENT));
		entity.setScoreAnimalWelfare(product.getNormalisedScores().get(ANIMAL_WELFARE));
		entity.setScoreSocioEconomic(product.getNormalisedScores().get(SOCIO_ECONOMIC));
		entity.setScoreHealth(product.getNormalisedScores().get(HEALTH));
		return entity;
	}

	private static UserBadgeEntity toEntity(PurchasedBasketEntity basket, Badge badge) {
		var entity = new UserBadgeEntity();
		entity.setUserProfileId(basket.getUserProfileId());
		entity.setBadge(badge);
		entity.setPurchasedBasketId(basket.getId());
		entity.setEarnedAt(basket.getRecordedAt());
		return entity;
	}
}
