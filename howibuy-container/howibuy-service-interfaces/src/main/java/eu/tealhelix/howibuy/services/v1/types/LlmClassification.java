package eu.tealhelix.howibuy.services.v1.types;

import eu.tealhelix.howibuy.v1.model.Classification;
import eu.tealhelix.howibuy.v1.model.ClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.MatchDiagnostics;

/**
 * How the LLM classified a Retailer Product.
 */
public sealed interface LlmClassification {
	/**
	 * The LLM matched the product to an archetype or to none, and its attempt resolved the product's Classification.
	 *
	 * @param attempt        The attempt recorded; it has no archetype when none matches the product
	 * @param classification The Classification resolved from the attempts of the product
	 */
	record Recorded(ClassificationAttempt attempt, Classification classification) implements LlmClassification {
	}

	/**
	 * The reply of the LLM was malformed, so nothing was recorded and the product stays pending.
	 *
	 * @param diagnostics The path the matching took through the SAFAD taxonomy until the malformed reply
	 */
	record Malformed(MatchDiagnostics diagnostics) implements LlmClassification {
	}
}
