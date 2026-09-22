package eu.tealhelix.sfc.jaxrs.v1;

import java.util.Optional;

import eu.tealhelix.sfc.services.v1.types.AnsweredQuestion;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * The result of asking for the question before a category's frontier: either the {@link #question} to step back to,
 * carrying the answer already given for it (with {@link #atStart} {@code false}), or, when the user has answered nothing
 * in the category, {@code atStart = true} and no question. Navigation never crosses into another category.
 */
@Schema(description = "The question to step back to in a category, or the word that there is nothing behind the user.")
public record PreviousQuestionResponse(
		@Schema(description = "True when the user has answered nothing in the category, in which case no question is named.")
		boolean atStart,
		@Schema(description = "The question to step back to, carrying the answer already given, or null when the user is at the start.")
		QuestionDto question) {
	static PreviousQuestionResponse of(Optional<AnsweredQuestion> previous) {
		return previous
				.map(answered -> new PreviousQuestionResponse(false, QuestionDto.from(answered)))
				.orElseGet(() -> new PreviousQuestionResponse(true, null));
	}
}
