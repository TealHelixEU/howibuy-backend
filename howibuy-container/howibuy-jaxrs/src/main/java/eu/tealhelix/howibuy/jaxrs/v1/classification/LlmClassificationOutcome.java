package eu.tealhelix.howibuy.jaxrs.v1.classification;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "How a matching by the LLM ended. MATCHED: the LLM matched the product to an archetype. NO_MATCH: it matched the product to none. MALFORMED_REPLY: its reply was malformed.")
public enum LlmClassificationOutcome {
	MATCHED,
	NO_MATCH,
	MALFORMED_REPLY
}
