package eu.tealhelix.howibuy.v1.model;

import org.immutables.value.Value;

/**
 * One classifier's answer to which archetype a Retailer Product is, from its name in one language, as the classifier
 * gives it.
 */
@Value.Immutable
public interface ClassificationAttemptInput extends ClassificationAttemptData {
}
