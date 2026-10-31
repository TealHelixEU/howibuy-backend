package eu.tealhelix.howibuy.services.model;

import java.util.Map;

import eu.tealhelix.catalogue.v1.types.HasProductKey;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.ProductGrade;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import org.immutables.value.Value;

/**
 * A scored product of a {@link PurchasedBasket}, with the scores of its archetype under the Weighting Profile that was
 * in force.
 */
@Value.Immutable
public interface PurchasedBasketProduct extends HasProductKey {
	/**
	 * The archetype the product is classified to, whose scores the product took.
	 */
	ArchetypeProductId getArchetypeProductId();

	double getOverallScore();

	ProductGrade getProductGrade();

	/**
	 * The four Normalised Scores of the archetype, one for every dimension.
	 */
	Map<ScoredSustainabilityDimension, Double> getNormalisedScores();
}
