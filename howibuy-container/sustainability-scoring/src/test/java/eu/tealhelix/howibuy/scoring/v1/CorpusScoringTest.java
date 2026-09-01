package eu.tealhelix.howibuy.scoring.v1;

import static eu.tealhelix.howibuy.scoring.v1.ProductIndicatorsTestUtils.product;
import static eu.tealhelix.howibuy.scoring.v1.WeightProfileTestUtils.indicatorsOnly;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CHILD_LABOUR;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CORRUPTION;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CorpusScoringTest {
	private static final List<ProductIndicators> CORPUS = List.of(
			product("a", "Nutriscore_A", Map.of(CHILD_LABOUR, 10.0)),
			product("b", "Nutriscore_C", Map.of(CHILD_LABOUR, 20.0)));

	/**
	 * Scoring the whole corpus is the expensive half of the method and its result depends only on the indicator
	 * weights, which are the same for every user. Recomputing it per assessment would be the difference between
	 * microseconds and a full pass over thousands of products.
	 */
	@Test
	void scoresTheCorpusOncePerDistinctSetOfIndicatorWeights() {
		var sut = new CorpusScoring(CORPUS);
		var weights = indicatorsOnly(Map.of(CHILD_LABOUR, 1.0));

		assertThat(sut.scoredWith(weights)).isSameAs(sut.scoredWith(weights));
	}

	@Test
	void scoresTheCorpusAgainForDifferentIndicatorWeights() {
		var sut = new CorpusScoring(CORPUS);

		var one = sut.scoredWith(indicatorsOnly(Map.of(CHILD_LABOUR, 1.0)));
		var other = sut.scoredWith(indicatorsOnly(Map.of(CORRUPTION, 1.0)));

		assertThat(one).isNotSameAs(other);
	}

	/**
	 * The dimension weights personalise the overall score but play no part in scoring the corpus, so two profiles that
	 * agree on the indicators must share one scored corpus — otherwise every user would get their own copy.
	 */
	@Test
	void ignoresTheDimensionWeightsWhenDecidingWhetherTheCorpusIsAlreadyScored() {
		var sut = new CorpusScoring(CORPUS);
		var indicatorWeights = Map.of(CHILD_LABOUR, 1.0);

		var scientific = WeightProfileTestUtils.profile(indicatorWeights, Map.of(
				eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT, 0.25,
				eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE, 0.25,
				eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIAL, 0.25,
				eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH, 0.25));
		var personal = WeightProfileTestUtils.profile(indicatorWeights, Map.of(
				eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH, 1.0));

		assertThat(sut.scoredWith(scientific)).isSameAs(sut.scoredWith(personal));
	}
}
