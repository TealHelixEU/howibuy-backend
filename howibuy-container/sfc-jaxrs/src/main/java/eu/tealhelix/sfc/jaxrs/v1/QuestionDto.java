package eu.tealhelix.sfc.jaxrs.v1;

import eu.tealhelix.sfc.services.v1.types.AnsweredQuestion;
import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.QuestionId;
import eu.tealhelix.sfc.v1.types.ScaleOption;

/**
 * A compass question with its prompt resolved for the requested language and, on the review reads, the user's current
 * answer ({@code null} when they have not answered it yet).
 */
public record QuestionDto(QuestionId id, short position, String text, ScaleOption answer) {
	static QuestionDto from(AnsweredQuestion answered) {
		var question = answered.question();
		return new QuestionDto(question.getId(), question.getPosition(), question.getText(), answered.answer().orElse(null));
	}

	static QuestionDto from(Question question) {
		return new QuestionDto(question.getId(), question.getPosition(), question.getText(), null);
	}
}
