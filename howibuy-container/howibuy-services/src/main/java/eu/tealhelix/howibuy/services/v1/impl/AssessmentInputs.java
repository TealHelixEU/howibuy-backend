package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.services.v1.ProductAssessmentService.MAX_PRODUCTS;

import java.util.List;
import java.util.Objects;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;

/**
 * The checks of the inputs that the assessments of several products share.
 */
final class AssessmentInputs {
	private AssessmentInputs() {
	}

	/**
	 * Refuse a missing language, a missing list of product keys, more than {@link
	 * eu.tealhelix.howibuy.services.v1.ProductAssessmentService#MAX_PRODUCTS} keys, and a missing key.
	 */
	static void requireLanguageAndProductKeys(String language, List<ProductKey> productKeys) {
		RequiredInputMissingException.throwIfRequiredInputMissing("language", language);
		RequiredInputMissingException.throwIfRequiredInputMissing("productKeys", productKeys);
		if (productKeys.size() > MAX_PRODUCTS) {
			throw BadInputValueException.fromInputNameAndHint("productKeys", "There must be at most " + MAX_PRODUCTS);
		}
		if (productKeys.stream().anyMatch(Objects::isNull)) {
			throw RequiredInputMissingException.fromRequiredInputName("productKeys");
		}
	}
}
