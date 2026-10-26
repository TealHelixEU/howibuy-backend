package eu.tealhelix.cataloguesync.howibuy;

/**
 * How a classification by HowiBuy's LLM ended. A match and a no match are recorded; after a malformed reply nothing is,
 * and the product stays pending.
 */
public enum LlmClassificationOutcome {
	MATCHED,
	NO_MATCH,
	MALFORMED_REPLY
}
