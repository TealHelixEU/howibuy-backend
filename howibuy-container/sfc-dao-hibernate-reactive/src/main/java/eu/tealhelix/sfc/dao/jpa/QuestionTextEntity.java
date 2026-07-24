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
 * The per-language prompt of a {@link QuestionEntity}: one row per {@code (question, language)}, which together form the
 * primary key.
 */
@Entity
@IdClass(QuestionTextEntityId.class)
@Table(name = "TH_SFC_QUESTION_TEXT")
public class QuestionTextEntity {
	@Id
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "question_id")
	private QuestionEntity question;

	@Id
	@Column(name = "lang")
	private String lang;

	@Column(name = "text")
	private String text;

	public QuestionEntity getQuestion() {
		return question;
	}

	public void setQuestion(QuestionEntity question) {
		this.question = question;
	}

	public String getLang() {
		return lang;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}
}
