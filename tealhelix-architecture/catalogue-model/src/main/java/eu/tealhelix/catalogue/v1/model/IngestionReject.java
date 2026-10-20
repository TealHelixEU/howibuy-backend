package eu.tealhelix.catalogue.v1.model;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.types.Nullable;
import org.immutables.value.Value;

/**
 * A record of a Feed that was not applied, and why, so the Retailer can fix its export.
 */
@Value.Immutable
public interface IngestionReject {
	/**
	 * Where the record is in the Feed, counting the records from 1.
	 */
	long getPosition();

	/**
	 * The product key of the record; {@code null} if it could not be read.
	 */
	@Nullable
	ProductKey getProductKey();

	String getReason();
}
