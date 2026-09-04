package eu.tealhelix.howibuy.scoring.v1;

import java.util.Map;

import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;

/**
 * One archetype product as the scoring method sees it: its measured indicator values and the Nutri-Score its health
 * score is read from.
 *
 * @param productId the archetype product, and the key an alternative is reported by
 * @param categoryId the L2 category of the SAFAD taxonomy the product belongs to, at which substitutability is decided
 * @param agbCode the Agribalyse identifier; a stable external key, used to break ties between equally-scoring products
 * @param values the measured value of each indicator; an absent indicator contributes nothing
 * @param nutriScore the Nutri-Score label as WP3 writes it ({@code "Nutriscore_A"} to {@code "Nutriscore_E"}), or
 *        {@code "0"} for a product outside the scheme
 */
public record ProductIndicators(
		ArchetypeProductId productId,
		ArchetypeCategoryId categoryId,
		String agbCode,
		Map<SustainabilityIndicator, Double> values,
		String nutriScore) {
}
