package eu.tealhelix.howibuy.jaxrs.v1.classification;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "One classifier's answer to which Archetype Product a Retailer Product is, made from the product's name in one language.")
public record ClassificationAttemptRequest(
		@Schema(description = "The product classified.")
		String retailerProductId,
		@Schema(description = "The classification input hash of the product the attempt was made from, as read with the pending products.")
		String inputHash,
		@Schema(description = "ISO 639-1 code of the language of the name the attempt was made from, one the product is named in.")
		String language,
		@Schema(description = "Who made the attempt: MANUAL for one of us, EXPERT for a trusted domain source. A manual attempt takes precedence over an expert one, which takes precedence over the LLM's.")
		String classifier,
		@Schema(description = "The version of the classifier, e.g. the label of an expert's data set, so the attempt can be traced to it.")
		String classifierVersion,
		@Schema(description = "The Archetype Product the classifier matched the product to; null when it matched none.")
		String archetypeProductId
) {
}
