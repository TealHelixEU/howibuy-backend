package eu.tealhelix.cataloguesync.howibuy;

/**
 * @param retailerProductId The product for HowiBuy's LLM to classify
 * @param language ISO 639-1 code of the language of the name to classify the product from
 */
public record LlmClassificationRequest(String retailerProductId, String language) {
}
