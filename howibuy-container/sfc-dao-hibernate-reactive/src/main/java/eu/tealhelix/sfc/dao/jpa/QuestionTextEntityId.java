package eu.tealhelix.sfc.dao.jpa;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite identifier of a {@link QuestionTextEntity}: the question it localizes together with its language.
 */
public class QuestionTextEntityId implements Serializable {
	private UUID question;
	private String lang;

	public QuestionTextEntityId() {
	}

	public QuestionTextEntityId(UUID question, String lang) {
		this.question = question;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof QuestionTextEntityId that)) return false;
		return Objects.equals(question, that.question) && Objects.equals(lang, that.lang);
	}

	@Override
	public int hashCode() {
		return Objects.hash(question, lang);
	}
}
