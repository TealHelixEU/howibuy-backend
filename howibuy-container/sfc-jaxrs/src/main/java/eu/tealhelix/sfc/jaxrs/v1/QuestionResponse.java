package eu.tealhelix.sfc.jaxrs.v1;

import java.util.UUID;

import eu.tealhelix.sfc.v1.model.Question;

/**
 * A compass question with its prompt resolved for the requested language.
 */
public record QuestionResponse(UUID id, short position, String text) {
	static QuestionResponse from(Question question) {
		return new QuestionResponse(question.getId(), question.getPosition(), question.getText());
	}
}
