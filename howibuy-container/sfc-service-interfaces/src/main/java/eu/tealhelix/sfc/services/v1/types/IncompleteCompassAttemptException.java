package eu.tealhelix.sfc.services.v1.types;

import java.util.List;

import eu.tealhelix.sfc.v1.types.QuestionId;

/**
 * Thrown when a user tries to complete their compass attempt while some questions are still unanswered. Carries the
 * ids of the questions that remain, so the caller can point the user at exactly what is left (mapped to HTTP 422).
 */
public class IncompleteCompassAttemptException extends RuntimeException {
	private final List<QuestionId> unansweredQuestionIds;

	public IncompleteCompassAttemptException(List<QuestionId> unansweredQuestionIds) {
		super("Cannot complete the compass attempt, unanswered questions remain: " + unansweredQuestionIds.size());
		this.unansweredQuestionIds = List.copyOf(unansweredQuestionIds);
	}

	public List<QuestionId> getUnansweredQuestionIds() {
		return unansweredQuestionIds;
	}
}
