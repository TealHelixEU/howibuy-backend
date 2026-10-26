package eu.tealhelix.cataloguesync;

/**
 * An attempt at a product that failed.
 *
 * @param retailerProductId The product
 * @param language The language of the name the attempt was to be made from
 * @param error What went wrong
 */
public record ClassificationFailure(String retailerProductId, String language, String error) {
}
