package eu.tealhelix.howibuy.v1.model;

import java.time.LocalDateTime;

import org.immutables.value.Value;

/**
 * One classifier's answer to which archetype a Retailer Product is, from its name in one language, as recorded.
 */
@Value.Immutable
public interface ClassificationAttempt extends ClassificationAttemptData {
	/**
	 * When the attempt was recorded.
	 */
	LocalDateTime getCreatedAt();
}
