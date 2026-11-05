package eu.tealhelix.howibuy.scoring.v1;

import java.util.Map;
import java.util.Set;

import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;

/**
 * One archetype product as the scoring method sees it: its measured indicator values and its Nutri-Score.
 *
 * @param productId        the archetype product, and the key an alternative is reported by
 * @param categoryId       the L2 category of the SAFAD taxonomy the product belongs to, at which substitutability is
 *                         decided
 * @param agbCode          the Agribalyse identifier; a stable external key, used to break ties between equally-scoring
 *                         products
 * @param values           the measured value of each indicator; an absent indicator contributes nothing
 * @param nutriScore       the Nutri-Score label as WP3 writes it ({@code "Nutriscore_A"} to {@code "Nutriscore_E"}),
 *                         or {@code "0"} for a product without a Nutri-Score
 * @param nutriScorePoints the points behind the label, lower being healthier, which the health score is normalised
 *                         from; WP3's placeholder 0 for a product without a Nutri-Score
 */
public record ProductIndicators(
		ArchetypeProductId productId,
		ArchetypeCategoryId categoryId,
		String agbCode,
		Map<SustainabilityIndicator, Double> values,
		String nutriScore,
		int nutriScorePoints) {

	private static final Set<String> NUTRI_SCORES = Set.of("Nutriscore_A", "Nutriscore_B", "Nutriscore_C", "Nutriscore_D", "Nutriscore_E");

	/**
	 * Whether the product has a Nutri-Score, told by its label and never by its points. One that does not, infant food
	 * for one, has no health score, and the method leaves it out altogether.
	 */
	boolean hasNutriScore() {
		return NUTRI_SCORES.contains(nutriScore);
	}
}
