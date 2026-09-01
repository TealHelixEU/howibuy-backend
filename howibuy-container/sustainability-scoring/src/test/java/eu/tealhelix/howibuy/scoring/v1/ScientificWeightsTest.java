package eu.tealhelix.howibuy.scoring.v1;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIAL;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.ANIMAL_WELFARE_INDEX;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.ANTIBIOTIC_INDEX;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CLIMATE_CHANGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;
import org.junit.jupiter.api.Test;

/**
 * Guards the transcription of WP3's constants. Every indicator must be weighted and every impact category must be
 * PEF-normalised: an indicator silently missing from either table would contribute nothing to its dimension, which no
 * other test would notice.
 */
class ScientificWeightsTest {
	@Test
	void weightsEveryIndicator() {
		assertThat(ScientificWeights.profile().getIndicatorWeights()).containsOnlyKeys(SustainabilityIndicator.values());
	}

	@Test
	void weighsTheFourDimensionsEqually() {
		assertThat(ScientificWeights.profile().getDimensionWeights())
				.containsOnly(entry(ENVIRONMENT, 0.25), entry(ANIMAL_WELFARE, 0.25), entry(SOCIAL, 0.25), entry(HEALTH, 0.25));
	}

	@Test
	void weighsTheTwoAnimalWelfareIndicesEqually() {
		var weights = ScientificWeights.profile().getIndicatorWeights();

		assertThat(weights.get(ANIMAL_WELFARE_INDEX)).isEqualTo(0.5);
		assertThat(weights.get(ANTIBIOTIC_INDEX)).isEqualTo(0.5);
	}

	@Test
	void weighsTheIndicatorsOfEachDimensionToOne() {
		for (var dimension : new eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension[] {ENVIRONMENT, ANIMAL_WELFARE, SOCIAL}) {
			var sum = SustainabilityIndicator.of(dimension).stream()
					.mapToDouble(indicator -> ScientificWeights.profile().getIndicatorWeights().get(indicator))
					.sum();
			assertThat(sum).describedAs("weights of %s", dimension).isEqualTo(1.0, within(1e-5));
		}
	}

	@Test
	void pefNormalisesEveryEnvironmentalIndicatorAndNothingElse() {
		for (var indicator : SustainabilityIndicator.values()) {
			var scale = PefNormalization.weightScaleFor(indicator);
			if (indicator.getDimension() == ENVIRONMENT) {
				assertThat(scale).describedAs("PEF scale of %s", indicator).isNotEqualTo(1.0).isPositive();
			} else {
				assertThat(scale).describedAs("PEF scale of %s", indicator).isEqualTo(1.0);
			}
		}
	}

	@Test
	void scalesAWeightByThousandTimesTheReciprocalOfThePefNormalizationFactor() {
		assertThat(PefNormalization.weightScaleFor(CLIMATE_CHANGE)).isEqualTo(0.13239626500054527, within(1e-15));
	}

	private static org.assertj.core.data.MapEntry<eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension, Double> entry(
			eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension dimension, Double weight) {
		return org.assertj.core.data.MapEntry.entry(dimension, weight);
	}
}
