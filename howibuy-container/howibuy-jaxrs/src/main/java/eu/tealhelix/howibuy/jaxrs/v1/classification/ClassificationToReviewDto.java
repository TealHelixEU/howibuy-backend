package eu.tealhelix.howibuy.jaxrs.v1.classification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A Retailer Product whose current Classification was resolved from attempts that disagree, so one of us should look at it.")
public record ClassificationToReviewDto(
		@Schema(description = "The product.")
		String retailerProductId,
		@Schema(description = "The names of the product by the ISO 639-1 code of their language, in the order of the codes.")
		Map<String, String> names,
		@Schema(description = "The classification input hash of the product, from which the Classification and its attempts were made. A manual attempt that settles the review is recorded with it.")
		String inputHash,
		@Schema(description = "The Archetype Product the Classification matched the product to; null when it matched none.")
		String archetypeProductId,
		@Schema(description = "When the Classification was resolved.")
		LocalDateTime resolvedAt,
		@Schema(description = "The attempts made from the input hash, the latest first.")
		List<ClassificationAttemptDto> attempts
) {
}
