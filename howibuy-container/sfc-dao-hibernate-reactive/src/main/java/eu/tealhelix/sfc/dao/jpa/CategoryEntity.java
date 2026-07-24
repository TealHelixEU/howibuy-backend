package eu.tealhelix.sfc.dao.jpa;

import static jakarta.persistence.EnumType.STRING;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import eu.tealhelix.sfc.v1.types.SustainabilityDimension;

/**
 * A compass category. Deliberately thin: it carries only its {@link #getDimension() dimension}; all human-facing text
 * is localized in {@link CategoryTextEntity}.
 */
@Entity
@Table(name = "TH_SFC_CATEGORY")
public class CategoryEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Enumerated(STRING)
	@Column(name = "dimension")
	private SustainabilityDimension dimension;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public SustainabilityDimension getDimension() {
		return dimension;
	}

	public void setDimension(SustainabilityDimension dimension) {
		this.dimension = dimension;
	}
}
