package eu.tealhelix.howibuy.services.v1;

import java.util.List;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import io.smallrye.mutiny.Uni;

/**
 * Assesses the products a user scanned in a Catalogue. The {@code language} is the one the user is answered in: a
 * recommended product is named in it when it has a name in it, else in the Catalogue's primary language.
 */
public interface ProductAssessmentService {
	Uni<ProductAssessmentOutcome> assessSingleProduct(User user, CatalogueId catalogueId, ProductKey productKey, String language);

	Uni<List<ProductAssessmentOutcome>> assessMultipleProductsSync(User user, CatalogueId catalogueId, List<ProductKey> productKeys, String language);
}
