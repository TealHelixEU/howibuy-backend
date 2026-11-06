package eu.tealhelix.howibuy.services.v1.basket.types;

import eu.tealhelix.catalogue.v1.types.ProductKey;

/**
 * A product of a basket that has an Overall Score.
 *
 * @param productKey             the retailer's key of the product
 * @param scientificOverallScore the Overall Score under the scientific Weighting Profile
 * @param personalOverallScore   the Overall Score under the Weighting Profile in force for the user
 */
public record ScoredBasketProduct(ProductKey productKey, double scientificOverallScore, double personalOverallScore) {
}
