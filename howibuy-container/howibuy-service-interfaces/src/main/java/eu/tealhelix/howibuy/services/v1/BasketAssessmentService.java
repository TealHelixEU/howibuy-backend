package eu.tealhelix.howibuy.services.v1;

import java.time.OffsetDateTime;
import java.util.List;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.v1.model.BasketAssessmentOutcome;
import io.smallrye.mutiny.Uni;

/**
 * Assesses the basket of a user.
 */
public interface BasketAssessmentService {
	/**
	 * Assess the basket of a user before purchase.
	 */
	Uni<BasketAssessmentOutcome> assessBasketPrePurchase(User user, CatalogueId catalogueId, List<ProductKey> productKeys, String language);

	/**
	 * Assess the basket a user has bought, store it with the scores of its products and the badges it earns, add the XP
	 * it earns to the user's total and apply its effect to the user's Streak. There is no idempotency: every call is
	 * another purchase, so the caller must make it exactly once per purchase; a repeated call stores the basket again,
	 * adds its XP again, applies its effect to the Streak again and counts its products towards the badges again.
	 *
	 * @param user        The user who bought the basket
	 * @param catalogueId The Catalogue the user bought it from
	 * @param purchasedAt When the user bought it: the date and time at the shop, with the shop's offset from UTC, which
	 *                    decides the month the purchase counts in. It may run ahead of the clock of this application by a
	 *                    few minutes at most
	 * @param productKeys The products of the basket
	 * @param language    The language of the labels of the Basket Match and of the badges
	 * @return The outcome, whose total XP and Streak include this basket; a failure with
	 *         {@code BadInputValueException} for a purchase time in the future
	 */
	Uni<BasketAssessmentOutcome> assessPurchasedBasket(
			User user, CatalogueId catalogueId, OffsetDateTime purchasedAt, List<ProductKey> productKeys, String language);
}
