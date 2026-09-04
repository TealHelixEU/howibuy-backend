package eu.tealhelix.howibuy.scoring.v1;

import static eu.tealhelix.howibuy.scoring.v1.ProductIndicatorsTestUtils.idOf;
import static eu.tealhelix.howibuy.scoring.v1.ProductIndicatorsTestUtils.product;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIAL;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CHILD_LABOUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ScoredCorpusTest {
	private static final double TOLERANCE = 1e-12;
	private static final Map<eu.tealhelix.howibuy.v1.types.SustainabilityIndicator, Double> SOCIAL_ONLY = Map.of(CHILD_LABOUR, 1.0);

	@Test
	void readsTheHealthScoreOffTheNutriScore() {
		var corpus = ScoredCorpus.score(List.of(
				product("a", "Nutriscore_A", Map.of(CHILD_LABOUR, 1.0)),
				product("d", "Nutriscore_D", Map.of(CHILD_LABOUR, 1.0))), SOCIAL_ONLY);

		assertThat(require(corpus, "a").normalisedScores().get(HEALTH)).isEqualTo(1.0);
		assertThat(require(corpus, "d").normalisedScores().get(HEALTH)).isEqualTo(0.25);
	}

	@Test
	void normalisesEveryDimensionAcrossTheWholeCorpus() {
		var corpus = ScoredCorpus.score(List.of(
				product("low", "Nutriscore_A", Map.of(CHILD_LABOUR, 10.0)),
				product("mid", "Nutriscore_A", Map.of(CHILD_LABOUR, 20.0)),
				product("high", "Nutriscore_A", Map.of(CHILD_LABOUR, 30.0))), SOCIAL_ONLY);

		assertThat(require(corpus, "low").normalisedScores().get(SOCIAL)).isEqualTo(1.0, within(TOLERANCE));
		assertThat(require(corpus, "mid").normalisedScores().get(SOCIAL)).isEqualTo(0.5, within(TOLERANCE));
		assertThat(require(corpus, "high").normalisedScores().get(SOCIAL)).isEqualTo(0.0, within(TOLERANCE));
	}

	@Test
	void leavesOutAProductThatHasNoHealthScore() {
		var corpus = ScoredCorpus.score(List.of(
				product("scoreable", "Nutriscore_A", Map.of(CHILD_LABOUR, 10.0)),
				product("unscoreable", "0", Map.of(CHILD_LABOUR, 20.0))), SOCIAL_ONLY);

		assertThat(corpus.getProducts()).extracting(ScoredProduct::agbCode).containsExactly("scoreable");
		assertThat(corpus.find(idOf("unscoreable"))).isEmpty();
	}

	/**
	 * A product that cannot be recommended still shapes the scale everything else is measured on — WP3 normalises the
	 * whole database and only then drops the products with no overall score. Were it dropped first, {@code mid} would
	 * be the worst product in the corpus and score 0 instead of 0.5.
	 */
	@Test
	void normalisesAgainstProductsThatAreThemselvesUnscoreable() {
		var corpus = ScoredCorpus.score(List.of(
				product("low", "Nutriscore_A", Map.of(CHILD_LABOUR, 10.0)),
				product("mid", "Nutriscore_A", Map.of(CHILD_LABOUR, 20.0)),
				product("worst", "0", Map.of(CHILD_LABOUR, 30.0))), SOCIAL_ONLY);

		assertThat(require(corpus, "mid").normalisedScores().get(SOCIAL)).isEqualTo(0.5, within(TOLERANCE));
	}

	@Test
	void combinesTheFourNormalisedScoresByTheirDimensionWeights() {
		var sut = new ScoredProduct(idOf("1"), ProductIndicatorsTestUtils.SOME_CATEGORY, "1",
				Map.of(ENVIRONMENT, 1.0, ANIMAL_WELFARE, 0.5, SOCIAL, 0.0, HEALTH, 0.25));

		var scientific = WeightProfileTestUtils.profile(Map.of(),
				Map.of(ENVIRONMENT, 0.25, ANIMAL_WELFARE, 0.25, SOCIAL, 0.25, HEALTH, 0.25));
		var healthLed = WeightProfileTestUtils.profile(Map.of(),
				Map.of(ENVIRONMENT, 0.1, ANIMAL_WELFARE, 0.1, SOCIAL, 0.1, HEALTH, 0.7));

		assertThat(sut.overallScore(scientific)).isEqualTo(0.4375, within(TOLERANCE));
		assertThat(sut.overallScore(healthLed)).isEqualTo(0.325, within(TOLERANCE));
	}

	private static ScoredProduct require(ScoredCorpus corpus, String agbCode) {
		return corpus.require(idOf(agbCode));
	}

	@Test
	void scoresAnEmptyCorpusWithoutFailing() {
		var corpus = ScoredCorpus.score(List.of(), SOCIAL_ONLY);

		assertThat(corpus.getProducts()).isEmpty();
	}
}
