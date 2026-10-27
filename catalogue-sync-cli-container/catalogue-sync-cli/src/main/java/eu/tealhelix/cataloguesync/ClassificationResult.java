package eu.tealhelix.cataloguesync;

import java.time.Duration;

/**
 * @param products The products attempted
 * @param matched The attempts that matched their product to an archetype
 * @param noMatch The attempts that matched their product to none
 * @param malformedReplies The attempts that got a malformed reply from the LLM, which left their product pending
 * @param failures The attempts that failed, which left their product pending
 * @param elapsed How long the classification took
 */
public record ClassificationResult(int products, int matched, int noMatch, int malformedReplies, int failures, Duration elapsed) {
	public int attempts() {
		return matched + noMatch + malformedReplies + failures;
	}
}
