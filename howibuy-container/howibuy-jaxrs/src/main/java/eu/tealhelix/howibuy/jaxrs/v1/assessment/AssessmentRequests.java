package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import static eu.tealhelix.common.web.RequestValuesHelper.nullable;

import java.util.List;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;

/**
 * The conversion that the requests of the assessments naming several products share.
 */
final class AssessmentRequests {
	private AssessmentRequests() {
	}

	/**
	 * @return The keys of the products of a request, as sent, {@code null} for those missing; {@code null} when the
	 *         request carries none
	 */
	static List<ProductKey> productKeys(List<String> productKeys) {
		return productKeys != null ? productKeys.stream().<ProductKey>map(productKey -> nullable(productKey, ProductKeyImpl::new)).toList() : null;
	}
}
