package eu.tealhelix.howibuy.jaxrs.v1.classification;

import eu.tealhelix.howibuy.v1.model.MatchDiagnostics;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "How the LLM matched the product data. Nothing was stored.")
public record LlmClassificationPreviewResponse(
		@Schema(description = "How the matching ended.")
		LlmClassificationOutcome outcome,
		@Schema(description = "The archetype the LLM matched the product to; null unless the outcome is MATCHED.")
		String archetypeProductId,
		@Schema(description = "The path the matching took through the SAFAD taxonomy, up to the archetype or to where it stopped.")
		MatchDiagnostics diagnostics
) {
}
