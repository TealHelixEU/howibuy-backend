package eu.tealhelix.howibuy.jaxrs.v1.classification;

import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Product data for the LLM to match to an Archetype Product, as it matches a Retailer Product from its name in one language, its characteristics and its tags.")
public record LlmClassificationPreviewRequest(
		@Schema(description = "The name of the product.")
		String name,
		@Schema(description = "ISO 639-1 code of the language everything textual in the product data is written in.")
		String language,
		@Schema(description = "Named characteristics of the product, in the language above; none when missing.")
		Map<String, String> characteristics,
		@Schema(description = "Tags of the product, in the language above; none when missing.")
		List<String> tags
) {
}
