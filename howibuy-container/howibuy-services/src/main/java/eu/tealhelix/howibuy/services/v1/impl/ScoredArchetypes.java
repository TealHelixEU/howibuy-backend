package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.GOOD_ENOUGH;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.NO_SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.SUGGESTION;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import eu.tealhelix.howibuy.scoring.v1.Alternatives;
import eu.tealhelix.howibuy.scoring.v1.ProductIndicators;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.scoring.v1.ScoredCorpus;
import eu.tealhelix.howibuy.scoring.v1.ScoredProduct;
import eu.tealhelix.howibuy.scoring.v1.SubstitutabilityMatrix;
import eu.tealhelix.howibuy.scoring.v1.SubstitutablePair;
import eu.tealhelix.howibuy.scoring.v1.SubstitutionSearch;
import eu.tealhelix.howibuy.scoring.v1.SubstitutionSettings;
import eu.tealhelix.howibuy.services.model.ArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.Substitutability;
import eu.tealhelix.howibuy.v1.model.AlternativeForProduct;
import eu.tealhelix.howibuy.v1.model.ImmutableAlternativeForProduct;
import eu.tealhelix.howibuy.v1.model.ImmutableRecommendedArchetype;
import eu.tealhelix.howibuy.v1.model.RecommendedArchetype;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.WeightProfile;

/**
 * The whole archetype corpus, scored and ready to answer "what should this user buy instead". Holds the scored
 * products, the substitutability matrix and what a recommendation says about each product, and turns the engine's
 * answer into the three alternatives an assessment reports.
 * <p>
 * Immutable and shared by every request; see {@link ArchetypeCorpus}, which loads it. The scoring itself is
 * independent of who is asking, so a request contributes only its own weighting profile.
 */
public final class ScoredArchetypes {
	private final ScoredCorpus scoredCorpus;
	private final SubstitutionSearch search;
	private final SubstitutionSettings settings;
	/**
	 * What an alternative says about the archetype it names, whichever ranking chose it.
	 */
	private final Map<ArchetypeProductId, RecommendedArchetype> archetypesByProductId;

	/**
	 * The best alternative under each of the three criteria, all three {@code NO_SUGGESTION} when the assessed product
	 * cannot be recommended against.
	 */
	public record BestAlternatives(
			AlternativeForProduct personal,
			AlternativeForProduct scientific,
			AlternativeForProduct combined) {

		static BestAlternatives none() {
			return new BestAlternatives(noSuggestion(), noSuggestion(), noSuggestion());
		}
	}

	public static ScoredArchetypes of(
			List<ArchetypeProductImpacts> corpus, List<Substitutability> matrix, SubstitutionSettings settings) {
		var scientificProfile = ScientificWeights.profile();
		var scoredCorpus = ScoredCorpus.score(corpus.stream().map(ScoredArchetypes::toProductIndicators).toList());
		var search = new SubstitutionSearch(
				scoredCorpus, SubstitutabilityMatrix.of(matrix.stream().map(ScoredArchetypes::toSubstitutablePair).toList()),
				scientificProfile, settings);

		var archetypesByProductId = new HashMap<ArchetypeProductId, RecommendedArchetype>(corpus.size());
		corpus.forEach(product -> archetypesByProductId.put(product.getId(), toRecommendedArchetype(product)));
		return new ScoredArchetypes(scoredCorpus, search, settings, Map.copyOf(archetypesByProductId));
	}

	/**
	 * What to recommend in place of the given archetype product, under the given personal weighting profile, among the
	 * archetypes the user's Catalogue stocks.
	 * <p>
	 * Nothing is recommended for a product outside the Nutri-Score scheme, which has no overall score to compare
	 * against; nor for one whose category nothing substitutes for at the configured level. Both are answers, not
	 * failures, and both read as {@code NO_SUGGESTION}.
	 */
	public BestAlternatives recommendationsFor(
			ArchetypeProductId referenceProductId, WeightProfile personalProfile, Set<ArchetypeProductId> stocked) {
		if (scoredCorpus.find(referenceProductId).isEmpty()) return BestAlternatives.none();

		var alternatives = search.find(referenceProductId, personalProfile, stocked);
		var scientificProfile = ScientificWeights.profile();
		ToDoubleFunction<ScoredProduct> personalScore = product -> product.overallScore(personalProfile);
		ToDoubleFunction<ScoredProduct> scientificScore = product -> product.overallScore(scientificProfile);
		return new BestAlternatives(
				alternative(alternatives, alternatives.bestPersonal(), personalScore),
				alternative(alternatives, alternatives.bestScientific(), scientificScore),
				alternative(alternatives, alternatives.bestCombined(), product -> settings.combinedScore(
						personalScore.applyAsDouble(product), scientificScore.applyAsDouble(product))));
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

	/**
	 * One ranking's winner, scored under that same ranking's criterion so that the two numbers explain the choice.
	 * The winner may be the reference product itself, which is the search saying nothing eligible beats it.
	 */
	private AlternativeForProduct alternative(
			Alternatives alternatives, Optional<ScoredProduct> winner, ToDoubleFunction<ScoredProduct> score) {
		if (winner.isEmpty()) return noSuggestion();

		var product = winner.get();
		var reference = alternatives.reference();
		return ImmutableAlternativeForProduct.builder()
				.type(product.productId().equals(reference.productId()) ? GOOD_ENOUGH : SUGGESTION)
				.archetype(archetypesByProductId.get(product.productId()))
				.referenceOverallScore(score.applyAsDouble(reference))
				.alternativeOverallScore(score.applyAsDouble(product))
				.build();
	}

	private static AlternativeForProduct noSuggestion() {
		return ImmutableAlternativeForProduct.builder().type(NO_SUGGESTION).build();
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
				product.getIndicatorValues(), product.getNutriScore());
	}

	private static SubstitutablePair toSubstitutablePair(Substitutability pair) {
		return new SubstitutablePair(pair.getFromCategoryId(), pair.getToCategoryId(), pair.getDegree());
	}

	private ScoredArchetypes(
			ScoredCorpus scoredCorpus, SubstitutionSearch search, SubstitutionSettings settings,
			Map<ArchetypeProductId, RecommendedArchetype> archetypesByProductId) {
		this.scoredCorpus = scoredCorpus;
		this.search = search;
		this.settings = settings;
		this.archetypesByProductId = archetypesByProductId;
	}
}
