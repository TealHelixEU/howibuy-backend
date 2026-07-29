package eu.tealhelix.sfc.jaxrs.v1;

import java.util.List;

import eu.tealhelix.sfc.v1.types.CategoryId;

/**
 * The questions of one category, grouped under its id, for the all-questions read.
 */
public record CategoryQuestionsResponse(CategoryId categoryId, List<QuestionDto> questions) {
}
