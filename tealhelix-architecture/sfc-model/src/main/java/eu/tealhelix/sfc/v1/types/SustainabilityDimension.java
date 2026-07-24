package eu.tealhelix.sfc.v1.types;

import eu.tealhelix.sfc.v1.model.Category;

/**
 * The fixed set of sustainability dimensions the compass covers. Each {@link Category} addresses exactly one
 * dimension. Persisted by name (as a string), not by ordinal, so the storage is stable against reordering.
 */
public enum SustainabilityDimension {
	ECOLOGICAL,
	SOCIAL,
	ECONOMIC,
	HEALTH,
	ANIMAL_WELFARE
}
