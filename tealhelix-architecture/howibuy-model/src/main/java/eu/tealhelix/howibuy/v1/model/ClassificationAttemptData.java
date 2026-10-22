package eu.tealhelix.howibuy.v1.model;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.Classifier;

/**
 * One classifier's answer to which archetype a Retailer Product is, from its name in one language.
 */
public interface ClassificationAttemptData {
	/**
	 * The language of the product name the attempt was made from.
	 */
	String getLanguage();

	Classifier getClassifier();

	/**
	 * The version of the classifier, such as the language model, prompt and glossary of the LLM, or the label of an
	 * expert's data set.
	 */
	String getClassifierVersion();

	/**
	 * The archetype the product was classified as; {@code null} when no archetype matches it.
	 */
	@Nullable
	ArchetypeProductId getArchetypeProductId();

	/**
	 * The classification input hash of the product when the attempt was made.
	 */
	String getInputHash();
}
