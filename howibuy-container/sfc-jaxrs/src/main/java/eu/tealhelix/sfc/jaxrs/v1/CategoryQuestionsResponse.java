package eu.tealhelix.sfc.jaxrs.v1;

import java.util.List;

import eu.tealhelix.sfc.v1.types.CategoryId;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * The questions of one category, grouped under its id, for the all-questions read.
 */
@Schema(description = "The questions of one category, grouped under its id.")
public record CategoryQuestionsResponse(
		@Schema(description = "The category the questions below belong to.")
		CategoryId categoryId,
		@Schema(description = "The questions of the category, in the order they are asked.")
		List<QuestionDto> questions
) {
}
