package eu.tealhelix.howibuy.v1.model;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.AlternativeForProductType;
import org.immutables.value.Value;

/**
 * Describes an alternative for a product.
 */
@Value.Immutable
public interface AlternativeForProduct {
	/**
	 * The type of the alternative. If the value is {@link AlternativeForProductType#SUGGESTION},
	 * the name field will contain the name of the alternative.
	 */
	AlternativeForProductType getType();

	@Nullable
	String getName();
}
