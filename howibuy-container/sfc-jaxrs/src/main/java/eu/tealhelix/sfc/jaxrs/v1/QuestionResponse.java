package eu.tealhelix.sfc.jaxrs.v1;

import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.QuestionId;

/**
 * A compass question with its prompt resolved for the requested language.
 */
public record QuestionResponse(QuestionId id, short position, String text) {
	static QuestionResponse from(Question question) {
		return new QuestionResponse(question.getId(), question.getPosition(), question.getText());
	}
}
