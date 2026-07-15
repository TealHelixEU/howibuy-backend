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
	 * The L1 (top-level) node of the SAFAD taxonomy path locating the term, e.g. {@code "Milk and dairy products"};
	 * null when unknown.
	 */
	@Column(name = "category_hint_l1")
	private String categoryHintL1;

	/**
	 * The L2 node of the SAFAD taxonomy path, e.g. {@code "Cheese"}; null when the term is not located that deep. Set
	 * only when {@link #categoryHintL1 L1} is set.
	 */
	@Column(name = "category_hint_l2")
	private String categoryHintL2;

	/**
	 * The L3 node of the SAFAD taxonomy path; null when the term is not located that deep. Set only when
	 * {@link #categoryHintL2 L2} is set.
	 */
	@Column(name = "category_hint_l3")
	private String categoryHintL3;

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

	public String getCategoryHintL1() {
		return categoryHintL1;
	}

	public void setCategoryHintL1(String categoryHintL1) {
		this.categoryHintL1 = categoryHintL1;
	}

	public String getCategoryHintL2() {
		return categoryHintL2;
	}

	public void setCategoryHintL2(String categoryHintL2) {
		this.categoryHintL2 = categoryHintL2;
	}

	public String getCategoryHintL3() {
		return categoryHintL3;
	}

	public void setCategoryHintL3(String categoryHintL3) {
		this.categoryHintL3 = categoryHintL3;
	}
}
