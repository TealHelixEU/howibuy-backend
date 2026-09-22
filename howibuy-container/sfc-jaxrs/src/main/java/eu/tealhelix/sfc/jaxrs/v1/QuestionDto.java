package eu.tealhelix.sfc.jaxrs.v1;

import eu.tealhelix.sfc.services.v1.types.AnsweredQuestion;
import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.QuestionId;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A compass question with its prompt resolved for the requested language and, on the review reads, the user's current
 * answer ({@code null} when they have not answered it yet).
 */
@Schema(description = "A compass question with its prompt resolved for the requested language.")
public record QuestionDto(
		@Schema(description = "The question, as an answer refers to it.")
		QuestionId id,
		@Schema(description = "Where the question sits in its category, counting from one.")
		short position,
		@Schema(description = "The question as it is put to the user, localized.")
		String text,
		@Schema(description = "The option the user picked, or null when they have not answered this question.")
		ScaleOption answer) {
	static QuestionDto from(AnsweredQuestion answered) {
		var question = answered.question();
		return new QuestionDto(question.getId(), question.getPosition(), question.getText(), answered.answer().orElse(null));
	}

	static QuestionDto from(Question question) {
		return new QuestionDto(question.getId(), question.getPosition(), question.getText(), null);
	}
}
