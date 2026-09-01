package eu.tealhelix.howibuy.dao.jpa;

import static jakarta.persistence.FetchType.LAZY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.tealhelix.howibuy.dao.jpa.values.ArchetypeSubstitutabilityPK;

/**
 * One substitutable pair of the WP3 substitutability matrix, read as: {@link #getFromCategory() from} may substitute
 * for {@link #getToCategory() to}. Both ends are L2 categories of the SAFAD taxonomy. Only substitutable pairs are
 * stored, so a missing row means the substitution is not allowed at any level.
 *
 * <p>
 * The relation is stored directionally even though the matrix WP3 delivered is symmetric, so that a future
 * asymmetric revision cannot silently invert. The diagonal is stored too — a category always substitutes for itself,
 * which is what lets the algorithm conclude that the product the user already has is the best available choice.
 */
@Entity
@Table(name = "TH_ARCHETYPE_SUBSTITUTABILITY")
@IdClass(ArchetypeSubstitutabilityPK.class)
public class ArchetypeSubstitutabilityEntity {
	@Id
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "from_category_id")
	private ArchetypeCategoryEntity fromCategory;

	@Id
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "to_category_id")
	private ArchetypeCategoryEntity toCategory;

	@Column(name = "degree")
	private short degree;

	public ArchetypeCategoryEntity getFromCategory() {
		return fromCategory;
	}

	public void setFromCategory(ArchetypeCategoryEntity fromCategory) {
		this.fromCategory = fromCategory;
	}

	public ArchetypeCategoryEntity getToCategory() {
		return toCategory;
	}

	public void setToCategory(ArchetypeCategoryEntity toCategory) {
		this.toCategory = toCategory;
	}

	public short getDegree() {
		return degree;
	}

	public void setDegree(short degree) {
		this.degree = degree;
	}
}
