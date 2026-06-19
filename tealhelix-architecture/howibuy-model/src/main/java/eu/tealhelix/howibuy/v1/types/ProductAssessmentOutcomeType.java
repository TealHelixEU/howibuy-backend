package eu.tealhelix.howibuy.v1.types;

/**
 * The overall outcome of the assessment.
 */
public enum ProductAssessmentOutcomeType {
	/**
	 * Assessment succeeded, details in the relevant fields of the response.
	 */
	SUCCESS,
	/**
	 * Could not match the given product to any known archetype.
	 */
	FAILURE_TO_IDENTIFY,
	/**
	 * Some other failure.
	 */
	FAILURE_OTHER
}
