package eu.tealhelix.howibuy.scoring.v1;

import static eu.tealhelix.howibuy.scoring.v1.ProductIndicatorsTestUtils.idOf;
import static eu.tealhelix.howibuy.scoring.v1.ProductIndicatorsTestUtils.product;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIO_ECONOMIC;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.ANIMAL_WELFARE_INDEX;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CHILD_LABOUR;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CLIMATE_CHANGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;

import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import org.junit.jupiter.api.Test;

class ScoredCorpusTest {
	private static final double TOLERANCE = 1e-12;

	/**
	 * Each product holds the same impact on every dimension and as many Nutri-Score points, over a corpus that holds its
	 * lowest and its highest value twice, so that the 1st and 99th percentiles are the minimum and the maximum. Taken as
	 * it is, an impact of 1 is a third of the way from 0 to 3; log-transformed around 1, the median of the positive
	 * impacts, it lands halfway between log 1 and log 4.
	 */
	@Test
	void normalisesEachDimensionFromItsOwnImpacts() {
		var corpus = ScoredCorpus.score(List.of(
				impactOf("a", 0), impactOf("b", 0),
				impactOf("c", 1), impactOf("d", 1), impactOf("e", 1),
				impactOf("f", 3), impactOf("g", 3)));

		Map<ScoredSustainabilityDimension, Double> scores = corpus.require(idOf("c")).normalisedScores();
		assertThat(scores.get(ENVIRONMENT)).isEqualTo(2.0 / 3.0, within(TOLERANCE));
		assertThat(scores.get(ANIMAL_WELFARE)).isEqualTo(0.5, within(TOLERANCE));
		assertThat(scores.get(SOCIO_ECONOMIC)).isEqualTo(0.5, within(TOLERANCE));
		assertThat(scores.get(HEALTH)).as("from the Nutri-Score points, taken as they are").isEqualTo(2.0 / 3.0, within(TOLERANCE));
		assertThat(corpus.require(idOf("a")).normalisedScores().values()).as("the lowest impact scores best").containsOnly(1.0);
		assertThat(corpus.require(idOf("g")).normalisedScores().values()).as("the highest impact scores worst").containsOnly(0.0);
	}

	@Test
	void leavesOutAProductThatHasNoNutriScore() {
		var corpus = ScoredCorpus.score(List.of(
				product("scoreable", "Nutriscore_A", Map.of(CHILD_LABOUR, 10.0)),
				product("unscoreable", "0", Map.of(CHILD_LABOUR, 20.0))));

		assertThat(corpus.getProducts()).extracting(ScoredProduct::agbCode).containsExactly("scoreable");
		assertThat(corpus.find(idOf("unscoreable"))).isEmpty();
	}

	/**
	 * A product without a Nutri-Score has no part in the scale either, as WP3 decided. Without {@code worst}, {@code mid}
	 * is the most impactful product of the scale, beyond its 99th percentile, and scores 0; scored with every product
	 * given, it sits halfway between the percentiles of 10, 20 and 30.
	 */
	@Test
	void leavesAProductWithoutANutriScoreOutOfTheScaleUnlessScoringEveryProduct() {
		var products = List.of(
				product("low", "Nutriscore_A", Map.of(CLIMATE_CHANGE, 10.0)),
				product("mid", "Nutriscore_A", Map.of(CLIMATE_CHANGE, 20.0)),
				product("worst", "0", Map.of(CLIMATE_CHANGE, 30.0)));

		assertThat(environmentOf(ScoredCorpus.score(products), "mid")).isEqualTo(0.0);

		var everyProduct = ScoredCorpus.scoreAll(products);
		assertThat(environmentOf(everyProduct, "mid")).isEqualTo(0.5, within(TOLERANCE));
		assertThat(everyProduct.find(idOf("worst"))).as("scored with the others, though it has no Nutri-Score").isPresent();
	}

	@Test
	void combinesTheFourNormalisedScoresByTheirDimensionWeights() {
		var sut = new ScoredProduct(idOf("1"), ProductIndicatorsTestUtils.SOME_CATEGORY, "1",
				Map.of(ENVIRONMENT, 1.0, ANIMAL_WELFARE, 0.5, SOCIO_ECONOMIC, 0.0, HEALTH, 0.25));

		var scientific = WeightProfileTestUtils.profile(Map.of(ENVIRONMENT, 0.25, ANIMAL_WELFARE, 0.25, SOCIO_ECONOMIC, 0.25, HEALTH, 0.25));
		var healthLed = WeightProfileTestUtils.profile(Map.of(ENVIRONMENT, 0.1, ANIMAL_WELFARE, 0.1, SOCIO_ECONOMIC, 0.1, HEALTH, 0.7));

		assertThat(sut.overallScore(scientific)).isEqualTo(0.4375, within(TOLERANCE));
		assertThat(sut.overallScore(healthLed)).isEqualTo(0.325, within(TOLERANCE));
	}

	@Test
	void scoresAnEmptyCorpusWithoutFailing() {
		var corpus = ScoredCorpus.score(List.of());

		assertThat(corpus.getProducts()).isEmpty();
	}

	/**
	 * A product with the given impact on every dimension: one indicator of each at that value, and as many Nutri-Score
	 * points. A single score is its indicator's value scaled by a constant, which normalisation cannot see.
	 */
	private static ProductIndicators impactOf(String agbCode, int impact) {
		return product(agbCode, "Nutriscore_A", impact, Map.of(
				CLIMATE_CHANGE, (double) impact,
				ANIMAL_WELFARE_INDEX, (double) impact,
				CHILD_LABOUR, (double) impact));
	}

	private static double environmentOf(ScoredCorpus corpus, String agbCode) {
		return corpus.require(idOf(agbCode)).normalisedScores().get(ENVIRONMENT);
	}
}
