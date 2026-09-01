package eu.tealhelix.howibuy.scoring.v1;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIAL;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

class MinMaxNormalisationTest {
	@Test
	void invertsSoThatTheLowestSingleScoreBecomesTheBestNormalisedScore() {
		var normalised = MinMaxNormalisation.normalize(new double[] {1.0, 2.0, 3.0}, OptionalDouble.empty());

		assertThat(normalised).containsExactly(1.0, 0.5, 0.0);
	}

	@Test
	void yieldsTheBestScoreForEveryProductWhenTheyAreAllEquallyImpactful() {
		var normalised = MinMaxNormalisation.normalize(new double[] {2.0, 2.0}, OptionalDouble.empty());

		assertThat(normalised).containsExactly(1.0, 1.0);
	}

	/**
	 * The asymmetry is WP3's and is deliberate: the maximum ignores outliers so that one extreme product cannot
	 * compress every other score towards the top of the range, but the minimum is taken over everything. Here the
	 * range is 1..3 rather than 1..99, so the middle product lands at 0.5 rather than near 1.
	 */
	@Test
	void takesTheMaximumOverNonOutliersOnlyButTheMinimumOverEverything() {
		var normalised = MinMaxNormalisation.normalize(new double[] {1.0, 2.0, 3.0, 99.0}, OptionalDouble.of(4.0));

		assertThat(normalised).startsWith(1.0, 0.5, 0.0);
	}

	@Test
	void floorsOutliersAtTheWorstPossibleScoreRatherThanLettingThemGoNegative() {
		var normalised = MinMaxNormalisation.normalize(new double[] {1.0, 3.0, 99.0}, OptionalDouble.of(4.0));

		assertThat(normalised[2]).isEqualTo(0.0);
	}

	@Test
	void keepsTheThresholdItselfInsideTheRange() {
		var normalised = MinMaxNormalisation.normalize(new double[] {0.0, 4.0, 5.0}, OptionalDouble.of(4.0));

		assertThat(normalised).containsExactly(1.0, 0.0, 0.0);
	}

	/**
	 * Nothing is left to scale against when the threshold excludes the whole corpus, so the threshold is ignored rather
	 * than producing scores against an infinite range.
	 */
	@Test
	void fallsBackToTheWholeCorpusWhenEveryProductIsAnOutlier() {
		var normalised = MinMaxNormalisation.normalize(new double[] {10.0, 20.0, 30.0}, OptionalDouble.of(4.0));

		assertThat(normalised).containsExactly(1.0, 0.5, 0.0);
	}

	@Test
	void appliesWp3sOutlierThresholdsToTheDimensionsThatHaveThem() {
		assertThat(MinMaxNormalisation.outlierThresholdFor(ENVIRONMENT)).hasValue(4.0);
		assertThat(MinMaxNormalisation.outlierThresholdFor(ANIMAL_WELFARE)).hasValue(100.0);
		assertThat(MinMaxNormalisation.outlierThresholdFor(SOCIAL)).isEmpty();
		assertThat(MinMaxNormalisation.outlierThresholdFor(HEALTH)).isEmpty();
	}
}
