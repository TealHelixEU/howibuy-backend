package eu.tealhelix.howibuy.scoring.v1;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIO_ECONOMIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Most corpora below hold their lowest and their highest value at least twice. The 1st percentile then falls between
 * two equal values and is the minimum, the 99th the maximum, so the expected scores are plain fractions of the range.
 */
class PercentileNormalisationTest {
	private static final double TOLERANCE = 1e-12;

	@Test
	void takesAQuantileByLinearInterpolationBetweenTheTwoNearestValues() {
		var sorted = new double[] {10.0, 20.0, 40.0};

		assertThat(PercentileNormalisation.quantile(sorted, 0.25)).isEqualTo(15.0, within(TOLERANCE));
		assertThat(PercentileNormalisation.quantile(sorted, 0.75)).isEqualTo(30.0, within(TOLERANCE));
		assertThat(PercentileNormalisation.quantile(sorted, 0.5)).isEqualTo(20.0);
	}

	@Test
	void takesTheExtremeQuantilesAtTheEndsOfTheValues() {
		var sorted = new double[] {10.0, 20.0, 40.0};

		assertThat(PercentileNormalisation.quantile(sorted, 0.0)).isEqualTo(10.0);
		assertThat(PercentileNormalisation.quantile(sorted, 1.0)).isEqualTo(40.0);
	}

	@Test
	void invertsSoThatTheLowestImpactBecomesTheBestScore() {
		var normalised = PercentileNormalisation.normalise(new double[] {0.0, 0.0, 1.0, 3.0, 3.0});

		assertThat(normalised).containsExactly(new double[] {1.0, 1.0, 2.0 / 3.0, 0.0, 0.0}, within(TOLERANCE));
	}

	/**
	 * Over 0, 10, ... 100 the 1st percentile is 1 and the 99th is 99: the two ends of the corpus lie beyond the anchors
	 * and are held at the best and the worst score, while 50 sits halfway between the anchors.
	 */
	@Test
	void anchorsTheScaleAtThe1stAnd99thPercentilesAndClampsWhatLiesBeyondThem() {
		var impacts = new double[11];
		for (int i = 0; i < impacts.length; i++) {
			impacts[i] = 10.0 * i;
		}

		var normalised = PercentileNormalisation.normalise(impacts);

		assertThat(normalised[0]).isEqualTo(1.0);
		assertThat(normalised[5]).isEqualTo(0.5, within(TOLERANCE));
		assertThat(normalised[10]).isEqualTo(0.0);
	}

	/**
	 * The median of the positive impacts here is 1, so the log transform takes 0, 1 and 3 to log 1, log 2 and log 4,
	 * and 1 lands halfway. The median of all ten impacts would be 0.5 instead, and put 1 at {@code 1 - log 3 / log 7}.
	 * A negative impact counts as zero, as it would otherwise have no logarithm.
	 */
	@Test
	void logTransformsAroundTheMedianOfThePositiveImpactsBeforeAnchoring() {
		var normalised = PercentileNormalisation.normaliseLogTransformed(new double[] {-2.0, -2.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 3.0, 3.0});

		assertThat(normalised).containsExactly(new double[] {1.0, 1.0, 1.0, 1.0, 1.0, 0.5, 0.5, 0.5, 0.0, 0.0}, within(TOLERANCE));
	}

	@Test
	void logTransformsAnimalWelfareAndSocioEconomicOnly() {
		var impacts = new double[] {0.0, 0.0, 1.0, 1.0, 1.0, 3.0, 3.0};

		assertThat(PercentileNormalisation.normalise(ENVIRONMENT, impacts)[2]).isEqualTo(2.0 / 3.0, within(TOLERANCE));
		assertThat(PercentileNormalisation.normalise(ANIMAL_WELFARE, impacts)[2]).isEqualTo(0.5, within(TOLERANCE));
		assertThat(PercentileNormalisation.normalise(SOCIO_ECONOMIC, impacts)[2]).isEqualTo(0.5, within(TOLERANCE));
		assertThat(PercentileNormalisation.normalise(HEALTH, impacts)[2]).isEqualTo(2.0 / 3.0, within(TOLERANCE));
	}

	@Test
	void givesEveryProductTheBestScoreWhenTheyAreAllEquallyImpactful() {
		assertThat(PercentileNormalisation.normalise(new double[] {2.0, 2.0})).containsExactly(1.0, 1.0);
		assertThat(PercentileNormalisation.normalise(new double[] {2.0})).containsExactly(1.0);
		assertThat(PercentileNormalisation.normaliseLogTransformed(new double[] {2.0, 2.0})).containsExactly(1.0, 1.0);
	}

	@Test
	void givesEveryProductTheBestScoreWhenNoneHasAPositiveImpactToLogTransformAround() {
		assertThat(PercentileNormalisation.normaliseLogTransformed(new double[] {0.0, -1.0, 0.0})).containsExactly(1.0, 1.0, 1.0);
	}

	@Test
	void normalisesNothingToNothing() {
		assertThat(PercentileNormalisation.normalise(new double[0])).isEmpty();
		assertThat(PercentileNormalisation.normaliseLogTransformed(new double[0])).isEmpty();
	}
}
