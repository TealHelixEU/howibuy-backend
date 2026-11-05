package eu.tealhelix.howibuy.services.v1.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.random.RandomGenerator;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.scoring.v1.ProductIndicators;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.scoring.v1.ScoredCorpus;
import eu.tealhelix.howibuy.scoring.v1.ScoredProduct;
import eu.tealhelix.howibuy.scoring.v1.SubstitutabilityMatrix;
import eu.tealhelix.howibuy.scoring.v1.SubstitutablePair;
import eu.tealhelix.howibuy.scoring.v1.SubstitutionSearch;
import eu.tealhelix.howibuy.services.model.ArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.Substitutability;
import eu.tealhelix.howibuy.v1.model.AlternativeForProduct;
import eu.tealhelix.howibuy.v1.model.ImmutableAlternativeForProduct;
import eu.tealhelix.howibuy.v1.model.ImmutableRecommendedArchetype;
import eu.tealhelix.howibuy.v1.model.RecommendedArchetype;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.RecommendationType;
import eu.tealhelix.howibuy.v1.types.WeightProfile;

/**
 * The whole archetype corpus, scored and ready to answer "is there something better to buy". Holds the scored
 * products, the substitutability matrix and what an Alternative says about each product, and turns the engine's
 * answer into the terms of an assessment's outcome.
 * <p>
 * Immutable and shared by every request; see {@link ArchetypeCorpus}, which loads it. The scoring itself is
 * independent of who is asking, so a request contributes only its own weighting profile and random source.
 */
public final class ScoredArchetypes {
	private final ScoredCorpus scoredCorpus;
	private final SubstitutionSearch search;
	/**
	 * What an Alternative says about the archetype it names.
	 */
	private final Map<ArchetypeProductId, RecommendedArchetype> archetypesByProductId;

	/**
	 * A Recommendation in the terms of an assessment's outcome.
	 *
	 * @param type                  The Recommendation
	 * @param referenceOverallScore The scientific Overall Score of the Reference Product, missing for
	 *                              {@link RecommendationType#NO_SCORE}
	 * @param alternatives          The Alternatives, without the products of the Catalogue that stand for them;
	 *                              non-empty exactly for {@link RecommendationType#RECOMMENDED}
	 */
	public record ProductRecommendation(
			RecommendationType type,
			@Nullable Double referenceOverallScore,
			List<AlternativeForProduct> alternatives) {
	}

	public static ScoredArchetypes of(List<ArchetypeProductImpacts> corpus, List<Substitutability> matrix) {
		var scoredCorpus = ScoredCorpus.score(corpus.stream().map(ScoredArchetypes::toProductIndicators).toList());
		var search = new SubstitutionSearch(
				scoredCorpus, SubstitutabilityMatrix.of(matrix.stream().map(ScoredArchetypes::toSubstitutablePair).toList()));

		var archetypesByProductId = new HashMap<ArchetypeProductId, RecommendedArchetype>(corpus.size());
		corpus.forEach(product -> archetypesByProductId.put(product.getId(), toRecommendedArchetype(product)));
		return new ScoredArchetypes(scoredCorpus, search, Map.copyOf(archetypesByProductId));
	}

	/**
	 * The Recommendation for the given archetype product, under the given personal weighting profile, among the
	 * archetypes the user's Catalogue stocks.
	 *
	 * @param random The source of the draw that picks the Alternative
	 */
	public ProductRecommendation recommendationFor(
			ArchetypeProductId referenceProductId, WeightProfile personalProfile, Set<ArchetypeProductId> stocked, RandomGenerator random) {
		var recommendation = search.recommend(referenceProductId, personalProfile, stocked, random);
		OptionalDouble referenceOverallScore = recommendation.referenceOverallScore();
		return new ProductRecommendation(
				recommendation.type(),
				referenceOverallScore.isPresent() ? referenceOverallScore.getAsDouble() : null,
				recommendation.alternatives().stream().map(this::alternative).toList());
	}

	/**
	 * Told on the scientific Overall Score, the only one sure to beat the Reference Product's.
	 */
	private AlternativeForProduct alternative(ScoredProduct product) {
		return ImmutableAlternativeForProduct.builder()
				.archetype(archetypesByProductId.get(product.productId()))
				.overallScore(product.overallScore(ScientificWeights.profile()))
				.build();
	}

	/**
	 * @return The scored archetype product, empty for one outside the Nutri-Score scheme, which has no overall score
	 */
	public Optional<ScoredProduct> find(ArchetypeProductId productId) {
		return scoredCorpus.find(productId);
	}

	/**
	 * @return The name of the archetype product, empty if it is not in the corpus
	 */
	public Optional<String> nameOf(ArchetypeProductId productId) {
		return Optional.ofNullable(archetypesByProductId.get(productId)).map(RecommendedArchetype::getName);
	}

	private static RecommendedArchetype toRecommendedArchetype(ArchetypeProductImpacts product) {
		return ImmutableRecommendedArchetype.builder()
				.id(product.getId())
				.name(product.getName())
				.l1Category(product.getL1CategoryName())
				.l2Category(product.getL2CategoryName())
				.l3Category(product.getL3CategoryName())
				.build();
	}

	private static ProductIndicators toProductIndicators(ArchetypeProductImpacts product) {
		return new ProductIndicators(
				product.getId(), product.getL2CategoryId(), product.getAgbCode(),
				product.getIndicatorValues(), product.getNutriScore(), product.getNutriScorePoints());
	}

	private static SubstitutablePair toSubstitutablePair(Substitutability pair) {
		return new SubstitutablePair(pair.getFromCategoryId(), pair.getToCategoryId(), pair.getDegree());
	}

	private ScoredArchetypes(
			ScoredCorpus scoredCorpus, SubstitutionSearch search, Map<ArchetypeProductId, RecommendedArchetype> archetypesByProductId) {
		this.scoredCorpus = scoredCorpus;
		this.search = search;
		this.archetypesByProductId = archetypesByProductId;
	}
}
