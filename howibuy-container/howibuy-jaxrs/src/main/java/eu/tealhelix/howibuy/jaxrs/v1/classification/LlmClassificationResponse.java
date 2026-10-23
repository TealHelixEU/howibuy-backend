package eu.tealhelix.howibuy.jaxrs.v1.classification;

import eu.tealhelix.howibuy.v1.model.MatchDiagnostics;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "How the LLM classified a Retailer Product.")
public record LlmClassificationResponse(
		@Schema(description = "How the classification ended.")
		Outcome outcome,
		@Schema(description = "The attempt recorded; null when the reply of the LLM was malformed.")
		ClassificationAttemptDto attempt,
		@Schema(description = "The Classification the attempts of the product resolved to; null when the reply of the LLM was malformed.")
		ClassificationDto classification,
		@Schema(description = "The path the matching took through the SAFAD taxonomy, up to the archetype or to where it stopped.")
		MatchDiagnostics diagnostics
) {
	@Schema(name = "LlmClassificationOutcome", description = "How the classification ended. MATCHED: the LLM matched the product to an archetype. NO_MATCH: it matched the product to none, which is recorded too. MALFORMED_REPLY: its reply was malformed, so nothing was recorded and the product stays pending.")
	public enum Outcome {
		MATCHED,
		NO_MATCH,
		MALFORMED_REPLY
	}
}
