package eu.tealhelix.howibuy.scoring.v1;

import java.util.Map;

import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.WeightProfile;

/**
 * An archetype product with each of its four dimensions normalised against the whole corpus, so that the scores are
 * comparable with each other and with every other product's, and higher always means more sustainable.
 * <p>
 * The overall score is deliberately not a field: it depends on a weighting profile, and every product carries at
 * least two — the scientific one and the user's.
 */
public record ScoredProduct(
		ArchetypeProductId productId,
		ArchetypeCategoryId categoryId,
		String agbCode,
		Map<ScoredSustainabilityDimension, Double> normalisedScores) {

	/**
	 * The four normalised scores combined under the given profile's dimension weights.
	 */
	public double overallScore(WeightProfile profile) {
		double overall = 0.0;
		for (var dimension : ScoredSustainabilityDimension.values()) {
			overall += profile.getDimensionWeights().get(dimension) * normalisedScores.get(dimension);
		}
		return overall;
	}
}
