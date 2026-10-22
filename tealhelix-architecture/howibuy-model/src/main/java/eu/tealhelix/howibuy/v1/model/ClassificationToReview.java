package eu.tealhelix.howibuy.v1.model;

import java.util.List;
import java.util.Map;

import eu.tealhelix.howibuy.v1.types.HasRetailerProductId;
import org.immutables.value.Value;

/**
 * A Retailer Product whose current Classification was resolved from attempts that disagree, so one of us should look at
 * it.
 */
@Value.Immutable
public interface ClassificationToReview extends HasRetailerProductId {
	/**
	 * The names of the product by the ISO 639-1 code of their language, in the order of the codes.
	 */
	Map<String, String> getNames();

	Classification getClassification();

	/**
	 * The attempts made from the input hash of the Classification, the latest first.
	 */
	List<ClassificationAttempt> getAttempts();
}
