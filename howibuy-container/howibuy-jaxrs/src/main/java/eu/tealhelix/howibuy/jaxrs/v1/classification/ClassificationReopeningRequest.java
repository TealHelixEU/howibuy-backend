package eu.tealhelix.howibuy.jaxrs.v1.classification;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The classifier version whose Classifications to reopen, after the classifier improved.")
public record ClassificationReopeningRequest(
		@Schema(description = "The classifier: MANUAL, EXPERT or LLM.")
		String classifier,
		@Schema(description = "The version of the classifier, as its attempts record it.")
		String classifierVersion
) {
}
