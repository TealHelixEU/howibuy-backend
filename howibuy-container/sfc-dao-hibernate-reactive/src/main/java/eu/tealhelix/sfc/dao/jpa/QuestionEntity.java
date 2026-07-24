package eu.tealhelix.sfc.dao.jpa;

import static jakarta.persistence.FetchType.LAZY;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A compass question, ordered within its {@link #getCategory() category} by {@link #getPosition() position}. Its
 * prompt is localized in {@link QuestionTextEntity}.
 */
@Entity
@Table(name = "TH_SFC_QUESTION")
public class QuestionEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "category_id")
	private CategoryEntity category;

	@Column(name = "position")
	private short position;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public CategoryEntity getCategory() {
		return category;
	}

	public void setCategory(CategoryEntity category) {
		this.category = category;
	}

	public short getPosition() {
		return position;
	}

	public void setPosition(short position) {
		this.position = position;
	}
}
