package eu.tealhelix.howibuy.jaxrs.v1.classification;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Which Retailer Product the LLM is to classify, and from its name in which language.")
public record LlmClassificationRequest(
		@Schema(description = "The product to classify.")
		String retailerProductId,
		@Schema(description = "ISO 639-1 code of the language of the name to classify the product from, one the product is named in.")
		String language
) {
}
