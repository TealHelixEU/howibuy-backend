package eu.tealhelix.howibuy.v1.model;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.Classifier;
import eu.tealhelix.howibuy.v1.types.RetailerProductId;
import org.immutables.value.Value;

/**
 * How the assessment identified the Archetype Product of a scanned product: the Retailer Product it found and the
 * Classification of that product. A field is null when the identification did not get that far.
 */
@Value.Immutable
public interface IdentificationDiagnostics {
	/**
	 * The Retailer Product found by the key.
	 */
	RetailerProductId getRetailerProductId();

	/**
	 * The classifier whose attempts decided the Classification of the product.
	 */
	@Nullable
	Classifier getClassifier();

	/**
	 * Whether the attempts the Classification was resolved from disagree.
	 */
	@Nullable
	Boolean getNeedsReview();

	/**
	 * Whether the product waits to be classified again: it changed since its Classification was resolved, or the
	 * Classification was reopened.
	 */
	@Nullable
	Boolean getPending();

	/**
	 * The Archetype Product the product is.
	 */
	@Nullable
	ArchetypeProductId getArchetypeProductId();

	/**
	 * The name of the Archetype Product the product is.
	 */
	@Nullable
	String getArchetypeProductName();
}
