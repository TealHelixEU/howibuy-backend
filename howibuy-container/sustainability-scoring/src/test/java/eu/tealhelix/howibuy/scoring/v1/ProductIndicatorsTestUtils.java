package eu.tealhelix.howibuy.scoring.v1;

import java.util.Map;
import java.util.UUID;

import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeCategoryIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;

/**
 * Builds archetype products for the scoring tests. Ids are derived from the {@code agbCode} so a failure names a
 * product a reader can find in the test, and so ordering by id is not accidentally the same as ordering by score.
 */
final class ProductIndicatorsTestUtils {
	static final ArchetypeCategoryId SOME_CATEGORY = categoryOf("category");

	static ArchetypeProductId idOf(String agbCode) {
		return new ArchetypeProductIdImpl(uuidOf(agbCode).toString());
	}

	static ArchetypeCategoryId categoryOf(String name) {
		return new ArchetypeCategoryIdImpl(uuidOf(name).toString());
	}

	private static UUID uuidOf(String name) {
		return UUID.nameUUIDFromBytes(name.getBytes());
	}

	static ProductIndicators product(String agbCode, String nutriScore, Map<SustainabilityIndicator, Double> values) {
		return product(agbCode, nutriScore, SOME_CATEGORY, values);
	}

	static ProductIndicators product(String agbCode, String nutriScore, ArchetypeCategoryId categoryId, Map<SustainabilityIndicator, Double> values) {
		return new ProductIndicators(idOf(agbCode), categoryId, agbCode, values, nutriScore);
	}

	private ProductIndicatorsTestUtils() {
		// NOOP
	}
}
