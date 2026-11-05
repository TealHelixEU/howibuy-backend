package eu.tealhelix.howibuy.scoring.v1;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;

/**
 * Every archetype product that can be assessed, with all four dimensions normalised against each other.
 * <p>
 * Normalisation is always over every product of the corpus that has a Nutri-Score. Scores are only ever meaningful
 * relative to the set they were normalised against, so scoring a subset would silently produce numbers that look like
 * the real ones and are not comparable to them.
 */
public final class ScoredCorpus {
	private final List<ScoredProduct> products;
	private final Map<ArchetypeProductId, ScoredProduct> byProductId;

	/**
	 * Scores the archetype products that have a Nutri-Score, normalising every dimension over them, with the indicators
	 * weighted by WP3's scientific weights.
	 * <p>
	 * A product without a Nutri-Score is left out before normalisation: it gets no scores, and has no part in the scale
	 * every other product is measured on, as WP3 decided.
	 * <p>
	 * Nothing here depends on who is asking: the dimension weights of a weighting profile belong to
	 * {@link ScoredProduct#overallScore}, so one scored corpus serves every user.
	 */
	public static ScoredCorpus score(List<ProductIndicators> corpus) {
		return scoreAll(corpus.stream().filter(ProductIndicators::hasNutriScore).toList());
	}

	/**
	 * Scores every given product, normalising every dimension over exactly them: a product without a Nutri-Score is
	 * scored too, on WP3's placeholder points. WP3's notebook computed the scores the engine is verified against this
	 * way; production goes through {@link #score}.
	 */
	static ScoredCorpus scoreAll(List<ProductIndicators> products) {
		Map<ScoredSustainabilityDimension, double[]> normalised = normaliseEveryDimension(products);

		var scored = new ArrayList<ScoredProduct>(products.size());
		for (int i = 0; i < products.size(); i++) {
			var product = products.get(i);
			var scores = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
			for (var entry : normalised.entrySet()) {
				scores.put(entry.getKey(), entry.getValue()[i]);
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
	 *                                  Nutri-Score and therefore no overall score
	 */
	public ScoredProduct require(ArchetypeProductId productId) {
		return find(productId).orElseThrow(() -> new IllegalArgumentException("Product is not in the scored corpus, id: " + productId.asString()));
	}

	private static Map<ScoredSustainabilityDimension, double[]> normaliseEveryDimension(List<ProductIndicators> products) {
		var singleScores = new EnumMap<ScoredSustainabilityDimension, double[]>(ScoredSustainabilityDimension.class);
		for (var dimension : ScoredSustainabilityDimension.values()) {
			singleScores.put(dimension, new double[products.size()]);
		}
		for (int i = 0; i < products.size(); i++) {
			for (var entry : SingleScoreAggregation.aggregate(products.get(i), ScientificWeights.indicatorWeights()).entrySet()) {
				singleScores.get(entry.getKey())[i] = entry.getValue();
			}
		}

		var normalised = new EnumMap<ScoredSustainabilityDimension, double[]>(ScoredSustainabilityDimension.class);
		singleScores.forEach((dimension, scores) -> normalised.put(dimension, PercentileNormalisation.normalise(dimension, scores)));
		return normalised;
	}

	private ScoredCorpus(List<ScoredProduct> products) {
		this.products = products;
		var byProductId = new HashMap<ArchetypeProductId, ScoredProduct>(products.size());
		products.forEach(product -> byProductId.put(product.productId(), product));
		this.byProductId = Map.copyOf(byProductId);
	}
}
