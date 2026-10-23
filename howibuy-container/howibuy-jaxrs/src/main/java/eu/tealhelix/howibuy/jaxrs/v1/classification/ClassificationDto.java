package eu.tealhelix.howibuy.jaxrs.v1.classification;

import java.time.LocalDateTime;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The one match of a Retailer Product to an Archetype Product, resolved from its Classification Attempts.")
public record ClassificationDto(
		@Schema(description = "The Archetype Product the Classification matched the product to; null when it matched none.")
		String archetypeProductId,
		@Schema(description = "The classification input hash of the product the Classification was resolved from.")
		String inputHash,
		@Schema(description = "Whether the attempts it was resolved from disagree, so one of us should look at it.")
		boolean needsReview,
		@Schema(description = "When the Classification was resolved.")
		LocalDateTime resolvedAt
) {
}
