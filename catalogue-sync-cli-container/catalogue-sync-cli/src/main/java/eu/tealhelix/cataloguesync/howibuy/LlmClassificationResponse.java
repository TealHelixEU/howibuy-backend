package eu.tealhelix.cataloguesync.howibuy;

/**
 * @param outcome How the classification by HowiBuy's LLM ended
 */
public record LlmClassificationResponse(LlmClassificationOutcome outcome) {
}
