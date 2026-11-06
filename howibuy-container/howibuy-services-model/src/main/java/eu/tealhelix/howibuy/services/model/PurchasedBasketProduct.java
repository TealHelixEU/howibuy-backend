package eu.tealhelix.howibuy.services.model;

import java.util.Map;

import eu.tealhelix.catalogue.v1.types.HasProductKey;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import org.immutables.value.Value;

/**
 * A scored product of a {@link PurchasedBasket}, with the scores of its archetype: its Overall Score under the Weighting
 * Profile that was in force for the user and under the scientific one, and its Normalised Scores.
 */
@Value.Immutable
public interface PurchasedBasketProduct extends HasProductKey {
	/**
	 * The archetype the product is classified to, whose scores the product took.
	 */
	ArchetypeProductId getArchetypeProductId();

	/**
	 * The Overall Score under the Weighting Profile that was in force for the user, which the Basket Match is judged on.
	 */
	double getPersonalOverallScore();

	/**
	 * The Overall Score under the scientific Weighting Profile, which the XP and the Problematic Products are judged on.
	 */
	double getScientificOverallScore();

	/**
	 * The four Normalised Scores of the archetype, one for every dimension.
	 */
	Map<ScoredSustainabilityDimension, Double> getNormalisedScores();
}
