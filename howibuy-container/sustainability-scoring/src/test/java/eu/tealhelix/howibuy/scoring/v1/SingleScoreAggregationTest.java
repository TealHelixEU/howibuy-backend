package eu.tealhelix.howibuy.scoring.v1;

import static eu.tealhelix.howibuy.scoring.v1.ProductIndicatorsTestUtils.product;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIAL;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.ANIMAL_WELFARE_INDEX;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.ANTIBIOTIC_INDEX;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CHILD_LABOUR;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CLIMATE_CHANGE;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CORRUPTION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.LAND_USE;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.MINERAL_USE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;

import org.junit.jupiter.api.Test;

class SingleScoreAggregationTest {
	private static final double TOLERANCE = 1e-12;

	/**
	 * PEF divides each impact by the yearly impact of an average person before weighting it, which is what makes the
	 * sixteen environmental indicators — measured in wildly different units — addable at all.
	 */
	@Test
	void scalesEveryEnvironmentalIndicatorByItsPefNormalizationFactor() {
		var sut = product("1", "Nutriscore_A", Map.of(CLIMATE_CHANGE, 2.0, MINERAL_USE, 0.001));
		var weights = Map.of(CLIMATE_CHANGE, 0.2106, MINERAL_USE, 0.0755);

		var singleScore = SingleScoreAggregation.aggregate(sut, weights).get(ENVIRONMENT);

		assertThat(singleScore).isEqualTo(1.2424502571116032, within(TOLERANCE));
	}

	@Test
	void weightsAnimalWelfareIndicesWithoutScalingThem() {
		var sut = product("1", "Nutriscore_A", Map.of(ANIMAL_WELFARE_INDEX, 8.0, ANTIBIOTIC_INDEX, 4.0));
		var weights = Map.of(ANIMAL_WELFARE_INDEX, 0.5, ANTIBIOTIC_INDEX, 0.5);

		var singleScore = SingleScoreAggregation.aggregate(sut, weights).get(ANIMAL_WELFARE);

		assertThat(singleScore).isEqualTo(6.0, within(TOLERANCE));
	}

	@Test
	void weightsSocialIndicatorsWithoutScalingThem() {
		var sut = product("1", "Nutriscore_A", Map.of(CHILD_LABOUR, 10.0, CORRUPTION, 20.0));
		var weights = Map.of(CHILD_LABOUR, 0.25, CORRUPTION, 0.75);

		var singleScore = SingleScoreAggregation.aggregate(sut, weights).get(SOCIAL);

		assertThat(singleScore).isEqualTo(17.5, within(TOLERANCE));
	}

	@Test
	void countsAnIndicatorTheProfileDoesNotWeightAsContributingNothing() {
		var sut = product("1", "Nutriscore_A", Map.of(CHILD_LABOUR, 10.0, CORRUPTION, 20.0));
		var weights = Map.of(CHILD_LABOUR, 1.0);

		var singleScore = SingleScoreAggregation.aggregate(sut, weights).get(SOCIAL);

		assertThat(singleScore).isEqualTo(10.0, within(TOLERANCE));
	}

	@Test
	void countsAnIndicatorTheProductDoesNotMeasureAsContributingNothing() {
		var sut = product("1", "Nutriscore_A", Map.of(CLIMATE_CHANGE, 2.0));
		var weights = Map.of(CLIMATE_CHANGE, 0.2106, LAND_USE, 0.0794);

		var singleScore = SingleScoreAggregation.aggregate(sut, weights).get(ENVIRONMENT);

		assertThat(singleScore).isEqualTo(2.0 * 0.2106 * 1000.0 / 7553.08316285117, within(TOLERANCE));
	}

	/**
	 * Health is read off the Nutri-Score, not aggregated from indicators, so it never has a single score.
	 */
	@Test
	void producesNoSingleScoreForHealth() {
		var sut = product("1", "Nutriscore_A", Map.of(CLIMATE_CHANGE, 2.0));

		var singleScores = SingleScoreAggregation.aggregate(sut, Map.of());

		assertThat(singleScores).containsOnlyKeys(ENVIRONMENT, ANIMAL_WELFARE, SOCIAL);
		assertThat(singleScores).doesNotContainKey(HEALTH);
	}
}
