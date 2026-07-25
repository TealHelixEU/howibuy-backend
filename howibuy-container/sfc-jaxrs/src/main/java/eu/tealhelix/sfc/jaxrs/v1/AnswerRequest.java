package eu.tealhelix.sfc.jaxrs.v1;

import eu.tealhelix.sfc.v1.types.ScaleOption;

/**
 * The body of an answer-upsert request: the scale option the user picked for the question.
 */
public record AnswerRequest(ScaleOption option) {
}
