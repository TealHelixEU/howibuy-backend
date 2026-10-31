package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import static eu.tealhelix.common.web.RequestValuesHelper.required;
import static eu.tealhelix.common.web.RequestValuesHelper.requiredText;

import java.util.List;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.types.validation.BadInputValueException;

/**
 * The validation that the requests of the assessments naming several products share.
 */
final class AssessmentRequests {
	private AssessmentRequests() {
	}

	/**
	 * @return The keys of the products of a request that carries a language and at most
	 *         {@link ProductAssessmentResource#MAX_PRODUCTS} of them, as sent
	 */
	static List<ProductKey> productKeys(String language, List<String> productKeys) {
		requiredText("language", language);
		required("productKeys", productKeys);
		if (productKeys.size() > ProductAssessmentResource.MAX_PRODUCTS) {
			throw BadInputValueException.fromInputNameAndHint("productKeys", "There must be at most " + ProductAssessmentResource.MAX_PRODUCTS);
		}
		return productKeys.stream().<ProductKey>map(productKey -> new ProductKeyImpl(requiredText("productKeys", productKey))).toList();
	}
}
