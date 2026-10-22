package eu.tealhelix.howibuy.v1.model;

import eu.tealhelix.common.types.Nullable;
import org.immutables.value.Value;

/**
 * The path the matching of a product took through the SAFAD taxonomy. A level is null when the matching
 * did not reach it.
 */
@Value.Immutable
public interface MatchDiagnostics {
	@Nullable
	String getL1Category();

	@Nullable
	String getL2Category();

	@Nullable
	String getL3Category();

	/**
	 * The name of the Archetype Product the product was matched to.
	 */
	@Nullable
	String getArchetypeProduct();
}
