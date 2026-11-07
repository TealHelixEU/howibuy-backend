package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import java.util.List;

import eu.tealhelix.howibuy.services.v1.ProductAssessmentService;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The products to assess in one request, scanned in the Catalogue that the token names.")
public record MultiProductAssessmentRequest(
		@Schema(description = "ISO 639-1 code of the language to answer in.")
		String language,
		@Schema(description = "The retailer's keys of the products, at most " + ProductAssessmentService.MAX_PRODUCTS + ". Each one is answered by an outcome of its own, in the order sent.")
		List<String> productKeys
) {
}
