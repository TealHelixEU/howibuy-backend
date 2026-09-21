package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.GOOD_ENOUGH;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.NO_SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.SUGGESTION;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

import eu.tealhelix.howibuy.scoring.v1.Alternatives;
import eu.tealhelix.howibuy.scoring.v1.CorpusScoring;
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
	private final Map<ArchetypeProductId, ArchetypeDescription> descriptionsByProductId;

	/**
	 * What an alternative says about the archetype it names, whichever ranking chose it: the product name and the SAFAD
	 * taxonomy path it sits in.
	 */
	private record ArchetypeDescription(String name, String l1Category, String l2Category, String l3Category) {
		static ArchetypeDescription of(ArchetypeProductImpacts product) {
			return new ArchetypeDescription(
					product.getName(), product.getL1CategoryName(), product.getL2CategoryName(), product.getL3CategoryName());
		}
	}

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
		var scoredCorpus = new CorpusScoring(corpus.stream().map(ScoredArchetypes::toProductIndicators).toList())
				.scoredWith(scientificProfile);
		var search = new SubstitutionSearch(
				scoredCorpus, SubstitutabilityMatrix.of(matrix.stream().map(ScoredArchetypes::toSubstitutablePair).toList()),
				scientificProfile, settings);

		var descriptionsByProductId = new HashMap<ArchetypeProductId, ArchetypeDescription>(corpus.size());
		corpus.forEach(product -> descriptionsByProductId.put(product.getId(), ArchetypeDescription.of(product)));
		return new ScoredArchetypes(scoredCorpus, search, settings, Map.copyOf(descriptionsByProductId));
	}

	/**
	 * What to recommend in place of the given archetype product, under the given personal weighting profile.
	 * <p>
	 * Nothing is recommended for a product outside the Nutri-Score scheme, which has no overall score to compare
	 * against; nor for one whose category nothing substitutes for at the configured level. Both are answers, not
	 * failures, and both read as {@code NO_SUGGESTION}.
	 */
	public BestAlternatives recommendationsFor(ArchetypeProductId referenceProductId, WeightProfile personalProfile) {
		if (scoredCorpus.find(referenceProductId).isEmpty()) return BestAlternatives.none();

		var alternatives = search.find(referenceProductId, personalProfile);
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
	 * One ranking's winner, scored under that same ranking's criterion so that the two numbers explain the choice.
	 * The winner may be the reference product itself, which is the search saying nothing eligible beats it.
	 */
	private AlternativeForProduct alternative(
			Alternatives alternatives, Optional<ScoredProduct> winner, ToDoubleFunction<ScoredProduct> score) {
		if (winner.isEmpty()) return noSuggestion();

		var product = winner.get();
		var reference = alternatives.reference();
		var description = descriptionsByProductId.get(product.productId());
		return ImmutableAlternativeForProduct.builder()
				.type(product.productId().equals(reference.productId()) ? GOOD_ENOUGH : SUGGESTION)
				.name(description.name())
				.l1Category(description.l1Category())
				.l2Category(description.l2Category())
				.l3Category(description.l3Category())
				.archetypeProductId(product.productId())
				.referenceOverallScore(score.applyAsDouble(reference))
				.alternativeOverallScore(score.applyAsDouble(product))
				.build();
	}

	private static AlternativeForProduct noSuggestion() {
		return ImmutableAlternativeForProduct.builder().type(NO_SUGGESTION).build();
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
			Map<ArchetypeProductId, ArchetypeDescription> descriptionsByProductId) {
		this.scoredCorpus = scoredCorpus;
		this.search = search;
		this.settings = settings;
		this.descriptionsByProductId = descriptionsByProductId;
	}
}
