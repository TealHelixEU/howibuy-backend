package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The product to assess, scanned in the Catalogue that the token names.")
public record SingleProductAssessmentRequest(
		@Schema(description = "The retailer's key of the product.")
		String productKey,
		@Schema(description = "ISO 639-1 code of the language to answer in.")
		String language
) {
}
