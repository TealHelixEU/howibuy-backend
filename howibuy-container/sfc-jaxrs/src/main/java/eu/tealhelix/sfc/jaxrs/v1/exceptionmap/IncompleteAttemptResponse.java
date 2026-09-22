package eu.tealhelix.sfc.jaxrs.v1.exceptionmap;

import java.util.List;

import eu.tealhelix.sfc.v1.types.QuestionId;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * The body returned when completion is refused because questions remain unanswered: the ids of exactly those
 * questions, so the client can guide the user to finish them.
 */
@Schema(description = "Why a completion was refused: the questions still unanswered.")
public record IncompleteAttemptResponse(
		@Schema(description = "The ids of exactly the questions still without an answer, so the client can guide the user to finish them.")
		List<QuestionId> unansweredQuestionIds
) {
}
