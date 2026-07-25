package eu.tealhelix.sfc.jaxrs.v1;

import java.util.Optional;

import eu.tealhelix.sfc.v1.model.Question;

/**
 * The result of asking for a category's next question: either the frontier {@link #question} to answer next (with
 * {@link #complete} {@code false}), or, when every question in the category is answered, {@code complete = true} and no
 * question. Navigation never crosses into another category.
 */
public record NextQuestionResponse(boolean complete, QuestionResponse question) {
	static NextQuestionResponse of(Optional<Question> frontier) {
		return frontier
				.map(question -> new NextQuestionResponse(false, QuestionResponse.from(question)))
				.orElseGet(() -> new NextQuestionResponse(true, null));
	}
}
