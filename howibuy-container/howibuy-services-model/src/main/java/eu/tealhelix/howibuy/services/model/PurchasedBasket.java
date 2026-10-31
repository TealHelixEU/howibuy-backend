package eu.tealhelix.howibuy.services.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.howibuy.v1.types.BasketAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.BasketGrade;
import org.immutables.value.Value;

/**
 * A Basket the Retailer registered as bought, as it is kept for the statistics: its verdict and the scores of its
 * scored products.
 */
@Value.Immutable
public interface PurchasedBasket {
	UserId getUserId();

	RetailerId getRetailerId();

	CatalogueId getCatalogueId();

	/**
	 * When the user bought the basket, as the Retailer states it.
	 */
	LocalDateTime getPurchasedAt();

	/**
	 * When HowiBuy registered the purchase.
	 */
	LocalDateTime getRecordedAt();

	BasketAssessmentOutcomeType getType();

	/**
	 * The Basket Grade, {@code null} when there was nothing to assess.
	 */
	@Nullable
	BasketGrade getBasketGrade();

	/**
	 * The XP the basket earned, 0 when there was nothing to assess.
	 */
	int getXpValue();

	/**
	 * The compass Attempt the personal Weighting Profile was derived from, {@code null} when the scientific profile was
	 * in force.
	 */
	@Nullable
	UUID getCompassAttemptId();

	/**
	 * The products of the basket that have an Overall Score, none when there was nothing to assess.
	 */
	List<PurchasedBasketProduct> getProducts();
}
