package eu.tealhelix.howibuy.services.model;

import eu.tealhelix.catalogue.v1.types.HasProductKey;
import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.model.Classification;
import eu.tealhelix.howibuy.v1.types.HasRetailerProductId;
import org.immutables.value.Value;

/**
 * A Retailer Product that a user scanned, with what the assessment learns of its archetype from its Classification.
 */
@Value.Immutable
public interface ProductToAssess extends HasRetailerProductId, HasProductKey {
	/**
	 * The Classification of the product, {@code null} if it was never classified.
	 */
	@Nullable
	Classification getClassification();

	/**
	 * Whether the product waits to be classified: it was never classified, it changed since it was, or its
	 * Classification was reopened.
	 */
	boolean isPending();
}
