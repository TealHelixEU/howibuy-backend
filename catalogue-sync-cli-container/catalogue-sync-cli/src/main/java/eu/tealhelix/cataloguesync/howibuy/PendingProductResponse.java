package eu.tealhelix.cataloguesync.howibuy;

import java.util.List;

/**
 * A product HowiBuy has waiting to be classified.
 *
 * @param retailerProductId The product
 * @param languages ISO 639-1 codes of the languages the product is named in, in alphabetical order
 */
public record PendingProductResponse(String retailerProductId, List<String> languages) {
}
