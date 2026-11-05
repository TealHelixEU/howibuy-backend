package eu.tealhelix.howibuy.scoring.v1;

import java.util.List;
import java.util.OptionalDouble;

import eu.tealhelix.howibuy.v1.types.RecommendationType;

/**
 * What the search answers for one Reference Product: whether there is something better to buy, and what.
 *
 * @param type                  Which of the four answers it is
 * @param referenceOverallScore The Reference Product's scientific Overall Score, absent for
 *                              {@link RecommendationType#NO_SCORE}
 * @param alternatives          The Archetype Products drawn from the Pool: one for {@link RecommendationType#RECOMMENDED},
 *                              none otherwise
 */
public record Recommendation(RecommendationType type, OptionalDouble referenceOverallScore, List<ScoredProduct> alternatives) {
	static Recommendation noScore() {
		return new Recommendation(RecommendationType.NO_SCORE, OptionalDouble.empty(), List.of());
	}

	static Recommendation goodEnough(double referenceOverallScore) {
		return new Recommendation(RecommendationType.GOOD_ENOUGH, OptionalDouble.of(referenceOverallScore), List.of());
	}

	static Recommendation noAlternative(double referenceOverallScore) {
		return new Recommendation(RecommendationType.NO_ALTERNATIVE, OptionalDouble.of(referenceOverallScore), List.of());
	}

	static Recommendation recommended(double referenceOverallScore, ScoredProduct alternative) {
		return new Recommendation(RecommendationType.RECOMMENDED, OptionalDouble.of(referenceOverallScore), List.of(alternative));
	}
}
