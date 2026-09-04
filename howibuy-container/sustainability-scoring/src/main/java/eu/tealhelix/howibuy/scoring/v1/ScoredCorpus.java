package eu.tealhelix.howibuy.scoring.v1;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;

/**
 * Every archetype product that can be assessed, with all four dimensions normalised against the corpus as a whole.
 * <p>
 * Normalisation is always over the complete corpus. Scores are only ever meaningful relative to the set they were
 * normalised against, so scoring a subset would silently produce numbers that look like the real ones and are not
 * comparable to them.
 */
public final class ScoredCorpus {
	private final List<ScoredProduct> products;
	private final Map<ArchetypeProductId, ScoredProduct> byProductId;

	/**
	 * Scores and normalises the whole corpus under the given indicator weights.
	 * <p>
	 * Products with no health score are dropped, but only after normalisation: a product that can never be
	 * recommended is still part of the range every other product is measured against, as it is in the reference
	 * implementation.
	 * <p>
	 * The dimension weights of a weighting profile are deliberately not a parameter. They belong to
	 * {@link ScoredProduct#overallScore}, and keeping them out of here is what makes a scored corpus shareable between
	 * users — see {@link CorpusScoring}.
	 */
	public static ScoredCorpus score(List<ProductIndicators> corpus, Map<SustainabilityIndicator, Double> indicatorWeights) {
		var normalised = normaliseEveryDimension(corpus, indicatorWeights);

		var scored = new ArrayList<ScoredProduct>(corpus.size());
		for (var i = 0; i < corpus.size(); i++) {
			var product = corpus.get(i);
			var health = HealthScore.forNutriScore(product.nutriScore());
			if (health.isEmpty()) continue;

			var scores = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
			for (var dimension : ScoredSustainabilityDimension.values()) {
				var byProduct = normalised.get(dimension);
				scores.put(dimension, byProduct == null ? health.getAsDouble() : byProduct[i]);
			}
			scored.add(new ScoredProduct(product.productId(), product.categoryId(), product.agbCode(), scores));
		}
		return new ScoredCorpus(List.copyOf(scored));
	}

	/**
	 * A corpus of products that have already been normalised together, for tests that need to state the scores
	 * outright. Production goes through {@link #score}, which is what guarantees the products were normalised as one
	 * set.
	 */
	static ScoredCorpus of(List<ScoredProduct> products) {
		return new ScoredCorpus(List.copyOf(products));
	}

	public List<ScoredProduct> getProducts() {
		return products;
	}

	public Optional<ScoredProduct> find(ArchetypeProductId productId) {
		return Optional.ofNullable(byProductId.get(productId));
	}

	/**
	 * @throws IllegalArgumentException if the product is not in the corpus, which for a scored corpus means it has no
	 *                                  health score and therefore no overall score
	 */
	public ScoredProduct require(ArchetypeProductId productId) {
		return find(productId).orElseThrow(() -> new IllegalArgumentException("Product is not in the scored corpus, id: " + productId.asString()));
	}

	private static Map<ScoredSustainabilityDimension, double[]> normaliseEveryDimension(
			List<ProductIndicators> corpus, Map<SustainabilityIndicator, Double> indicatorWeights) {
		var singleScores = new EnumMap<ScoredSustainabilityDimension, double[]>(ScoredSustainabilityDimension.class);
		for (var i = 0; i < corpus.size(); i++) {
			for (var entry : SingleScoreAggregation.aggregate(corpus.get(i), indicatorWeights).entrySet()) {
				singleScores.computeIfAbsent(entry.getKey(), dimension -> new double[corpus.size()])[i] = entry.getValue();
			}
		}

		var normalised = new EnumMap<ScoredSustainabilityDimension, double[]>(ScoredSustainabilityDimension.class);
		for (var entry : singleScores.entrySet()) {
			normalised.put(entry.getKey(), MinMaxNormalisation.normalize(entry.getValue(), MinMaxNormalisation.outlierThresholdFor(entry.getKey())));
		}
		return normalised;
	}

	private ScoredCorpus(List<ScoredProduct> products) {
		this.products = products;
		var byProductId = new HashMap<ArchetypeProductId, ScoredProduct>(products.size());
		products.forEach(product -> byProductId.put(product.productId(), product));
		this.byProductId = Map.copyOf(byProductId);
	}
}
