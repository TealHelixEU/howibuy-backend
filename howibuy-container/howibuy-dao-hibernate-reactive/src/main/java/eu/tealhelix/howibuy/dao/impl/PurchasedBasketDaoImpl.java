package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIAL;

import java.util.ArrayList;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.howibuy.dao.PurchasedBasketDao;
import eu.tealhelix.howibuy.dao.jpa.PurchasedBasketEntity;
import eu.tealhelix.howibuy.dao.jpa.PurchasedBasketProductEntity;
import eu.tealhelix.howibuy.services.model.PurchasedBasket;
import eu.tealhelix.howibuy.services.model.PurchasedBasketProduct;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class PurchasedBasketDaoImpl implements PurchasedBasketDao {
	/**
	 * The basket and its products are handed to the session in one call: a chain of a call per product would nest as deep
	 * as the products are many, and overflow the stack of the event loop.
	 */
	@Override
	public Uni<Void> store(ReactivePersistenceTxContext tx, PurchasedBasket basket) {
		var basketEntity = toEntity(basket);
		var entities = new ArrayList<>();
		entities.add(basketEntity);
		for (var product : basket.getProducts()) {
			entities.add(toEntity(basketEntity.getId(), product));
		}
		return tx.persistAll(entities.toArray());
	}

	private static PurchasedBasketEntity toEntity(PurchasedBasket basket) {
		var entity = new PurchasedBasketEntity();
		entity.setId(UUID.randomUUID());
		entity.setUserProfileId(basket.getUserId().asUuid());
		entity.setRetailerId(basket.getRetailerId().asUuid());
		entity.setCatalogueId(basket.getCatalogueId().asString());
		entity.setPurchasedAt(basket.getPurchasedAt());
		entity.setRecordedAt(basket.getRecordedAt());
		entity.setOutcomeType(basket.getType());
		entity.setBasketGrade(basket.getBasketGrade());
		entity.setXpValue(basket.getXpValue());
		entity.setCompassAttemptId(basket.getCompassAttemptId());
		return entity;
	}

	private static PurchasedBasketProductEntity toEntity(UUID basketId, PurchasedBasketProduct product) {
		var entity = new PurchasedBasketProductEntity();
		entity.setPurchasedBasketId(basketId);
		entity.setProductKey(product.getProductKey().asString());
		entity.setArchetypeProductId(product.getArchetypeProductId().asUuid());
		entity.setOverallScore(product.getOverallScore());
		entity.setProductGrade(product.getProductGrade());
		entity.setScoreEnvironment(product.getNormalisedScores().get(ENVIRONMENT));
		entity.setScoreAnimalWelfare(product.getNormalisedScores().get(ANIMAL_WELFARE));
		entity.setScoreSocial(product.getNormalisedScores().get(SOCIAL));
		entity.setScoreHealth(product.getNormalisedScores().get(HEALTH));
		return entity;
	}
}
