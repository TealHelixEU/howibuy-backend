package eu.tealhelix.howibuy.scoring.v1;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;
import eu.tealhelix.howibuy.v1.types.WeightProfile;

/**
 * Scores the archetype corpus, keeping the result for as long as the corpus is held.
 * <p>
 * Scoring and normalising thousands of products is the expensive half of the method, and it depends only on the
 * indicator weights — never on the dimension weights that personalise the overall score. So every user shares one
 * scored corpus, and personalising is four multiply-adds per product. See ADR 0005.
 */
public final class CorpusScoring {
	private final List<ProductIndicators> corpus;

	/**
	 * Keyed on the indicator weights, which are exactly what a scored corpus depends on — {@link ScoredCorpus#score}
	 * takes nothing else, so the key cannot drift away from what it identifies.
	 * <p>
	 * Nothing is ever evicted. Production has one set of indicator weights and therefore one entry. Should the
	 * weights within a dimension ever be personalised (the trigger to revisit ADR 0005), this becomes one scored corpus
	 * per user and needs a bound before it is switched on.
	 */
	private final Map<Map<SustainabilityIndicator, Double>, ScoredCorpus> scored = new ConcurrentHashMap<>();

	public CorpusScoring(List<ProductIndicators> corpus) {
		this.corpus = List.copyOf(corpus);
	}

	/**
	 * The corpus scored under the given profile's indicator weights, computed once and shared by every profile that
	 * weights the indicators the same way.
	 */
	public ScoredCorpus scoredWith(WeightProfile profile) {
		return scored.computeIfAbsent(profile.getIndicatorWeights(), weights -> ScoredCorpus.score(corpus, weights));
	}
}
