package eu.tealhelix.howibuy.scoring.v1;

import java.util.Map;
import java.util.OptionalDouble;

/**
 * The health dimension of an archetype product, read straight off its Nutri-Score.
 * <p>
 * Alone among the four dimensions, health is not aggregated from indicators and not min–max normalised: the five
 * grades map onto an evenly spaced scale that is already normalised and already points the right way, with A the best.
 * <p>
 * Transcribed from the {@code H_scientific_SS_MMnorm} case statement in
 * {@code TH_Algorithm_Implementation_v2026-05-20.Rmd}.
 */
final class HealthScore {
	private static final Map<String, Double> BY_NUTRI_SCORE = Map.of(
			"Nutriscore_A", 1.0,
			"Nutriscore_B", 0.75,
			"Nutriscore_C", 0.5,
			"Nutriscore_D", 0.25,
			"Nutriscore_E", 0.0);

	/**
	 * The health score of a product with the given Nutri-Score label, or empty for a product the scheme does not
	 * cover — alcoholic drinks and infant food, which the dataset labels {@code "0"}. Such a product gets no overall
	 * score at all, so it is neither assessed nor ever recommended.
	 */
	static OptionalDouble forNutriScore(String nutriScore) {
		if (nutriScore == null) return OptionalDouble.empty();
		var score = BY_NUTRI_SCORE.get(nutriScore);
		if (score == null) return OptionalDouble.empty();
		else return OptionalDouble.of(score);
	}

	private HealthScore() {
		// NOOP
	}
}
