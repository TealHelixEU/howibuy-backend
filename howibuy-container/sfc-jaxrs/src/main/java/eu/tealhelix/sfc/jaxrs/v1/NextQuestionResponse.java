package eu.tealhelix.sfc.jaxrs.v1;

import java.util.Optional;

import eu.tealhelix.sfc.v1.model.Question;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * The result of asking for a category's next question: either the frontier {@link #question} to answer next (with
 * {@link #complete} {@code false}), or, when every question in the category is answered, {@code complete = true} and no
 * question. Navigation never crosses into another category.
 */
@Schema(description = "The next question to put in front of the user in a category, or the word that there is none left.")
public record NextQuestionResponse(
		@Schema(description = "True when every question of the category is answered, in which case no question is named.")
		boolean complete,
		@Schema(description = "The question to answer next, or null when the category is complete.")
		QuestionDto question) {
	static NextQuestionResponse of(Optional<Question> frontier) {
		return frontier
				.map(question -> new NextQuestionResponse(false, QuestionDto.from(question)))
				.orElseGet(() -> new NextQuestionResponse(true, null));
	}
}
