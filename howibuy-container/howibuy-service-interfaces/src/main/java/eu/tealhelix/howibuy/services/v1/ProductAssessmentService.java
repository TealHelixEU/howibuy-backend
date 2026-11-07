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
	/**
	 * The most products an assessment of several products, of this service or of the basket, takes.
	 */
	int MAX_PRODUCTS = 400;

	/**
	 * Assess one product.
	 *
	 * @throws eu.tealhelix.common.types.validation.RequiredInputMissingException without a language or a product key
	 */
	Uni<ProductAssessmentOutcome> assessSingleProduct(User user, CatalogueId catalogueId, ProductKey productKey, String language);

	/**
	 * Assess several products, answering when all of them are done.
	 *
	 * @throws eu.tealhelix.common.types.validation.RequiredInputMissingException without a language or the product keys, or with a key missing
	 * @throws eu.tealhelix.common.types.validation.BadInputValueException       with more than {@link #MAX_PRODUCTS} product keys
	 */
	Uni<List<ProductAssessmentOutcome>> assessMultipleProductsSync(User user, CatalogueId catalogueId, List<ProductKey> productKeys, String language);
}
