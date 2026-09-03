package eu.tealhelix.howibuy.scoring.v1;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

import eu.tealhelix.howibuy.v1.types.WeightProfile;

/**
 * Finds the best substitute for a scanned product, three ways.
 * <p>
 * The search is four steps:
 * <ol>
 *     <li>Which categories may substitute for the reference product's category at the configured level</li>
 *     <li>Which of their products are at least as good as the reference on the <em>scientific</em> score</li>
 *     <li>How those survivors rank under the personal, scientific, and combined criteria</li>
 *     <li>And the best of each</li>
 * </ol>
 * <p>
 * The no-regression step deliberately uses the scientific score alone. A user's own priorities decide the order of
 * the answers, never which answers are allowed, so no weighting a user can express can steer them toward something
 * objectively worse.
 * <p>
 * Transcribed from the recommendation block of {@code TH_Algorithm_Implementation_v2026-05-20.Rmd}.
 */
public final class SubstitutionSearch {
	private final ScoredCorpus corpus;
	private final SubstitutabilityMatrix matrix;
	private final WeightProfile scientificProfile;
	private final SubstitutionSettings settings;

	public SubstitutionSearch(
			ScoredCorpus corpus,
			SubstitutabilityMatrix matrix,
			WeightProfile scientificProfile,
			SubstitutionSettings settings
	) {
		this.corpus = corpus;
		this.matrix = matrix;
		this.scientificProfile = scientificProfile;
		this.settings = settings;
	}

	/**
	 * @throws IllegalArgumentException if the reference product has no scores, which for an archetype product means it
	 *                                  has no Nutri-Score and so cannot be assessed at all
	 */
	public Alternatives find(UUID referenceProductId, WeightProfile personalProfile) {
		var reference = corpus.require(referenceProductId);
		var candidates = candidatesFor(reference);
		if (candidates.isEmpty()) return Alternatives.none(reference);

		return new Alternatives(
				reference,
				best(candidates, product -> product.overallScore(personalProfile)),
				best(candidates, product -> product.overallScore(scientificProfile)),
				best(candidates, product -> combinedScore(product, personalProfile)));
	}

	/**
	 * The reference product is among its own candidates: its category substitutes for itself, and the no-regression
	 * comparison is inclusive. That is what lets the search conclude the user already has the best available choice.
	 */
	private List<ScoredProduct> candidatesFor(ScoredProduct reference) {
		var eligibleCategories = matrix.categoriesSubstitutableFor(reference.categoryId(), settings.minimumDegree());
		var referenceScore = reference.overallScore(scientificProfile);
		return corpus.getProducts().stream()
				.filter(product -> eligibleCategories.contains(product.categoryId()))
				.filter(product -> product.overallScore(scientificProfile) >= referenceScore)
				.toList();
	}

	private double combinedScore(ScoredProduct product, WeightProfile personalProfile) {
		return settings.combinedScore(product.overallScore(personalProfile), product.overallScore(scientificProfile));
	}

	/**
	 * Ties break on the agb code, so that the same corpus always yields the same answer. The reference script's
	 * {@code which.max} takes whichever row happens to come first, which is an artefact of how the data was loaded
	 * rather than a decision.
	 */
	private static Optional<ScoredProduct> best(List<ScoredProduct> candidates, ToDoubleFunction<ScoredProduct> score) {
		var byScoreThenLowestCode = Comparator.comparingDouble(score)
				.thenComparing(Comparator.comparing(ScoredProduct::agbCode).reversed());
		return candidates.stream().max(byScoreThenLowestCode);
	}
}
