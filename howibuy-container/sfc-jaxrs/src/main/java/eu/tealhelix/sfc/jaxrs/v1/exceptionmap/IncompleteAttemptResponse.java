package eu.tealhelix.sfc.jaxrs.v1.exceptionmap;

import java.util.List;

import eu.tealhelix.sfc.v1.types.QuestionId;

/**
 * The body returned when completion is refused because questions remain unanswered: the ids of exactly those
 * questions, so the client can guide the user to finish them.
 */
public record IncompleteAttemptResponse(List<QuestionId> unansweredQuestionIds) {
}
