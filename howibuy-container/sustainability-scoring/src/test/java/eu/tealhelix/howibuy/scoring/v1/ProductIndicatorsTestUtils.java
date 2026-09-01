package eu.tealhelix.howibuy.scoring.v1;

import java.util.Map;
import java.util.UUID;

import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;

/**
 * Builds archetype products for the scoring tests. Ids are derived from the {@code agbCode} so a failure names a
 * product a reader can find in the test, and so ordering by id is not accidentally the same as ordering by score.
 */
final class ProductIndicatorsTestUtils {
	static final UUID SOME_CATEGORY = UUID.nameUUIDFromBytes("category".getBytes());

	static UUID idOf(String agbCode) {
		return UUID.nameUUIDFromBytes(agbCode.getBytes());
	}

	static ProductIndicators product(String agbCode, String nutriScore, Map<SustainabilityIndicator, Double> values) {
		return product(agbCode, nutriScore, SOME_CATEGORY, values);
	}

	static ProductIndicators product(String agbCode, String nutriScore, UUID categoryId, Map<SustainabilityIndicator, Double> values) {
		return new ProductIndicators(idOf(agbCode), categoryId, agbCode, values, nutriScore);
	}

	private ProductIndicatorsTestUtils() {
		// NOOP
	}
}
