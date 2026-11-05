package eu.tealhelix.howibuy.scoring.v1;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.WeightProfile;

/**
 * Answers, for the Archetype Product a scanned product was matched to, whether there is something better to buy, and
 * what.
 * <p>
 * The answer is reached in four steps, those of {@code paper_analysis_2026_07_31_v15.qmd} in its default
 * configuration:
 * <ol>
 *     <li>A Reference Product without a Nutri-Score has no score, and nothing is said about it</li>
 *     <li>One whose scientific Overall Score reaches the Gate is good enough, and no Alternative is sought</li>
 *     <li>For any other, the Pool: the stocked products of the categories substitutable for its own at the
 *     Substitutability Level, whose scientific Overall Score is strictly higher than its own</li>
 *     <li>And one product of the Pool, drawn at random in favour of a higher personal Overall Score</li>
 * </ol>
 * <p>
 * The draw is the only place a user's weights act. The Gate and the Pool are decided on the scientific profile alone,
 * so no weighting a user can express can steer them toward something objectively worse (ADR 0007).
 */
public final class SubstitutionSearch {
	/**
	 * {@code GATE_THRESHOLD} of the notebook: a Reference Product whose scientific Overall Score reaches it is good
	 * enough.
	 */
	static final double GATE = 0.80;
	/**
	 * {@code substitutability_level} of the notebook: the Pool takes the categories substitutable for the Reference
	 * Product's at this degree or above.
	 */
	private static final short SUBSTITUTABILITY_LEVEL = 4;
	/**
	 * {@code SHARPNESS} of the notebook: how strongly the draw favours a higher personal Overall Score. At 0 the draw
	 * would be uniform; at 15, a product scoring 0.1 higher is about four and a half times as likely to be drawn.
	 */
	private static final double SHARPNESS = 15;
	private static final WeightProfile SCIENTIFIC = ScientificWeights.profile();

	private final ScoredCorpus corpus;
	private final SubstitutabilityMatrix matrix;

	public SubstitutionSearch(ScoredCorpus corpus, SubstitutabilityMatrix matrix) {
		this.corpus = corpus;
		this.matrix = matrix;
	}

	/**
	 * @param referenceProductId The Archetype Product the scanned product was matched to
	 * @param personalProfile    The user's Weighting Profile, which the draw favours
	 * @param stocked            The Archetype Products the user's Catalogue stocks, the only ones the Pool may hold
	 * @param random             The source of the draw: the same source in the same state draws the same product from
	 *                           the same Pool
	 */
	public Recommendation recommend(
			ArchetypeProductId referenceProductId, WeightProfile personalProfile, Set<ArchetypeProductId> stocked, RandomGenerator random) {
		Optional<ScoredProduct> scored = corpus.find(referenceProductId);
		if (scored.isEmpty()) return Recommendation.noScore();
		var reference = scored.get();
		double referenceScore = reference.overallScore(SCIENTIFIC);
		if (referenceScore >= GATE) return Recommendation.goodEnough(referenceScore);

		List<ScoredProduct> pool = poolFor(reference, referenceScore, stocked);
		if (pool.isEmpty()) {
			return Recommendation.noAlternative(referenceScore);
		} else {
			return Recommendation.recommended(referenceScore, draw(pool, personalProfile, random));
		}
	}

	/**
	 * Strictly higher: a product only as good as the Reference Product is no improvement, and so the Reference Product
	 * never enters its own Pool, stocked or not. In the order of the AGB codes, so that the same random number draws the
	 * same product whatever order the corpus comes in.
	 */
	private List<ScoredProduct> poolFor(ScoredProduct reference, double referenceScore, Set<ArchetypeProductId> stocked) {
		Set<ArchetypeCategoryId> substitutableCategories = matrix.categoriesSubstitutableFor(reference.categoryId(), SUBSTITUTABILITY_LEVEL);
		return corpus.getProducts().stream()
				.filter(product -> stocked.contains(product.productId()))
				.filter(product -> substitutableCategories.contains(product.categoryId()))
				.filter(product -> product.overallScore(SCIENTIFIC) > referenceScore)
				.sorted(Comparator.comparing(ScoredProduct::agbCode))
				.toList();
	}

	/**
	 * A product of the Pool at random, each drawn with probability proportional to
	 * {@code exp(SHARPNESS × its personal Overall Score)}. That is the notebook's softmax, which it samples with the
	 * Gumbel-max trick and this by inversion: the same distribution, but not the same product from the same seed. Every
	 * exponent is taken less the highest, which leaves the probabilities as they are and keeps the weights from
	 * overflowing whatever the sharpness.
	 * <p>
	 * A Pool of one is returned as it is, without consulting the random source.
	 */
	private static ScoredProduct draw(List<ScoredProduct> pool, WeightProfile personalProfile, RandomGenerator random) {
		if (pool.size() == 1) return pool.getFirst();

		double[] personalScores = pool.stream().mapToDouble(product -> product.overallScore(personalProfile)).toArray();
		double highest = Double.NEGATIVE_INFINITY;
		for (double score : personalScores) {
			highest = Math.max(highest, score);
		}
		double[] weights = new double[personalScores.length];
		double total = 0.0;
		for (int i = 0; i < weights.length; i++) {
			weights[i] = Math.exp(SHARPNESS * (personalScores[i] - highest));
			total += weights[i];
		}

		// Each product takes its share of the total in turn; the last takes whatever remains, rounding included.
		double drawn = random.nextDouble() * total;
		double cumulative = 0.0;
		for (int i = 0; i < weights.length - 1; i++) {
			cumulative += weights[i];
			if (drawn < cumulative) return pool.get(i);
		}
		return pool.getLast();
	}
}
