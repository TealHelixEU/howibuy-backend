package eu.tealhelix.howibuy.services.v1.classification;

import java.util.List;
import java.util.Optional;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.model.ClassificationAttempt;

/**
 * The policy that resolves the one Classification of a Retailer Product from its Classification Attempts.
 */
public interface ClassificationResolution {
	/**
	 * @param inputHash              The product's current classification input hash; attempts made from another one
	 *                               do not count
	 * @param classificationLanguage The language the Retailer prefers its products classified in, if any
	 * @param attempts               The product's attempts, in any order
	 * @return The Classification, empty when no attempt was made from the current input hash
	 */
	Optional<Resolution> resolve(String inputHash, @Nullable String classificationLanguage, List<ClassificationAttempt> attempts);
}
