package eu.tealhelix.howibuy.v1.model;

import java.util.Set;

import eu.tealhelix.howibuy.v1.types.HasRetailerProductId;
import org.immutables.value.Value;

/**
 * A Retailer Product waiting to be classified: it has no Classification, or its Classification was resolved from
 * classification inputs other than its current ones.
 */
@Value.Immutable
public interface PendingProduct extends HasRetailerProductId {
	/**
	 * The classification input hash of the product's current names, characteristics and tags. A Classification Attempt
	 * made from them is recorded with it.
	 */
	String getInputHash();

	/**
	 * The languages the product is named in, the languages its Classification Attempts can be made in.
	 */
	Set<String> getLanguages();
}
