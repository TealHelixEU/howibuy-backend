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

	/**
	 * A product whose Nutri-Score points are WP3's placeholder 0, for a test that has no use for its health score.
	 */
	static ProductIndicators product(String agbCode, String nutriScore, Map<SustainabilityIndicator, Double> values) {
		return product(agbCode, nutriScore, 0, values);
	}

	static ProductIndicators product(String agbCode, String nutriScore, int nutriScorePoints, Map<SustainabilityIndicator, Double> values) {
		return new ProductIndicators(idOf(agbCode), SOME_CATEGORY, agbCode, values, nutriScore, nutriScorePoints);
	}

	private ProductIndicatorsTestUtils() {
		// NOOP
	}
}
