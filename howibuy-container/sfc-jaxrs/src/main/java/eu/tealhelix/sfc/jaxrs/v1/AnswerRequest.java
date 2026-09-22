package eu.tealhelix.sfc.jaxrs.v1;

import eu.tealhelix.sfc.v1.types.ScaleOption;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * The body of a SFC answer-upsert request: the scale option the user picked for the question.
 */
@Schema(description = "The answer to a SFC question: how much the user cares about what it asks.")
public record AnswerRequest(
		@Schema(description = "The scale option the user picked. Required — an answer without one is refused.")
		ScaleOption option
) {
}
