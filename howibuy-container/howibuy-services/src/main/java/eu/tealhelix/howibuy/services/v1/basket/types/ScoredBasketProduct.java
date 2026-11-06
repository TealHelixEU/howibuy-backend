package eu.tealhelix.howibuy.services.v1.basket.types;

import java.util.Map;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;

/**
 * A product of a basket that has an Overall Score.
 *
 * @param productKey             the retailer's key of the product
 * @param scientificOverallScore the Overall Score under the scientific Weighting Profile
 * @param personalOverallScore   the Overall Score under the Weighting Profile in force for the user
 * @param normalisedScores       the Normalised Scores of the product, one for every dimension
 */
public record ScoredBasketProduct(
		ProductKey productKey, double scientificOverallScore, double personalOverallScore, Map<ScoredSustainabilityDimension, Double> normalisedScores) {
}
