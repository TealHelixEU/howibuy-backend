package eu.tealhelix.sfc.jaxrs.v1;

import java.util.List;
import java.util.UUID;

/**
 * The questions of one category, grouped under its id, for the all-questions read.
 */
public record CategoryQuestionsResponse(UUID categoryId, List<QuestionResponse> questions) {
}
