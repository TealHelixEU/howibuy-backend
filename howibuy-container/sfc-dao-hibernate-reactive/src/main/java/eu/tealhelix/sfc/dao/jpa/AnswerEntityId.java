package eu.tealhelix.sfc.dao.jpa;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite identifier of an {@link AnswerEntity}: the attempt together with the question it answers.
 */
public class AnswerEntityId implements Serializable {
	private UUID attempt;
	private UUID question;

	public AnswerEntityId() {
	}

	public AnswerEntityId(UUID attempt, UUID question) {
		this.attempt = attempt;
		this.question = question;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AnswerEntityId that)) return false;
		return Objects.equals(attempt, that.attempt) && Objects.equals(question, that.question);
	}

	@Override
	public int hashCode() {
		return Objects.hash(attempt, question);
	}
}
