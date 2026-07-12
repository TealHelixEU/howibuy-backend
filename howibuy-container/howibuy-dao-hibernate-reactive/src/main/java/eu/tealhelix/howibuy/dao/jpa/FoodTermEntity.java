package eu.tealhelix.howibuy.dao.jpa;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A generic food term of a single language, mapping the native-language name of a food (brand-stripped, e.g.
 * {@code Ανθότυρος}) to an English canonical name and description. The glossary of these terms enriches a retailer
 * product name with world knowledge the classifier's model lacks, before the taxonomy descent.
 */
@Entity
@Table(name = "TH_FOOD_TERM")
public class FoodTermEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "lang")
	private String lang;

	@Column(name = "term")
	private String term;

	@Column(name = "canonical_en")
	private String canonicalEn;

	@Column(name = "description")
	private String description;

	/**
	 * Optional SAFAD taxonomy path locating the term, from L1 downward, node names separated by {@code " → "} (U+2192),
	 * e.g. {@code "Milk and dairy products → Cheese"}. Written as deep as known; null when unknown.
	 */
	@Column(name = "category_hint")
	private String categoryHint;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getLang() {
		return lang;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public String getCanonicalEn() {
		return canonicalEn;
	}

	public void setCanonicalEn(String canonicalEn) {
		this.canonicalEn = canonicalEn;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getCategoryHint() {
		return categoryHint;
	}

	public void setCategoryHint(String categoryHint) {
		this.categoryHint = categoryHint;
	}
}
