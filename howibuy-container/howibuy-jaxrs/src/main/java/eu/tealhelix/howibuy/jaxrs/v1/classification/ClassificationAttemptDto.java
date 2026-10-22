package eu.tealhelix.howibuy.jaxrs.v1.classification;

import java.time.LocalDateTime;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "One classifier's answer to which Archetype Product a Retailer Product is, made from the product's name in one language, as recorded.")
public record ClassificationAttemptDto(
		@Schema(description = "ISO 639-1 code of the language of the name the attempt was made from.")
		String language,
		@Schema(description = "Who made the attempt: MANUAL for one of us, EXPERT for a trusted domain source, LLM for the language model of this application.")
		String classifier,
		@Schema(description = "The version of the classifier, e.g. the language model, prompt and glossary of the LLM, or the label of an expert's data set.")
		String classifierVersion,
		@Schema(description = "The Archetype Product the classifier matched the product to; null when it matched none.")
		String archetypeProductId,
		@Schema(description = "When the attempt was recorded.")
		LocalDateTime createdAt
) {
}
