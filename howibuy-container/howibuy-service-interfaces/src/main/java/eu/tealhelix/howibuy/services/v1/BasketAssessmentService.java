package eu.tealhelix.howibuy.services.v1;

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
}
