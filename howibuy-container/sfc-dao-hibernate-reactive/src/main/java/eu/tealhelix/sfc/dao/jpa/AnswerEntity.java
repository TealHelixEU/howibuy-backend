package eu.tealhelix.sfc.dao.jpa;

import static jakarta.persistence.FetchType.LAZY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One user's answer to one question, within an {@link #getAttempt() attempt}. The {@code (attempt, question)} pair is
 * the primary key — a question is answered at most once per attempt, re-answering overwrites. The {@link #getValue()
 * value} is the chosen {@link eu.tealhelix.sfc.v1.types.ScaleOption scale option}'s 1–5 ordinal.
 */
@Entity
@IdClass(AnswerEntityId.class)
@Table(name = "TH_SFC_ANSWER")
public class AnswerEntity {
	@Id
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "attempt_id")
	private AttemptEntity attempt;

	@Id
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "question_id")
	private QuestionEntity question;

	@Column(name = "value")
	private short value;

	public AttemptEntity getAttempt() {
		return attempt;
	}

	public void setAttempt(AttemptEntity attempt) {
		this.attempt = attempt;
	}

	public QuestionEntity getQuestion() {
		return question;
	}

	public void setQuestion(QuestionEntity question) {
		this.question = question;
	}

	public short getValue() {
		return value;
	}

	public void setValue(short value) {
		this.value = value;
	}
}
