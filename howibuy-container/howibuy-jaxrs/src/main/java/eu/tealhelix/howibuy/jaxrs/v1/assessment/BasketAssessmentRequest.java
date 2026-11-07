package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import java.util.List;

import eu.tealhelix.howibuy.services.v1.ProductAssessmentService;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The products of a basket to assess, from the Catalogue that the token names.")
public record BasketAssessmentRequest(
		@Schema(description = "ISO 639-1 code of the language to answer in.")
		String language,
		@Schema(description = "The retailer's keys of the products in the basket, at most " + ProductAssessmentService.MAX_PRODUCTS + ".")
		List<String> productKeys
) implements BasketRequest {
}
