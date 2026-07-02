package eu.tealhelix.howibuy.dao.jpa;

import static jakarta.persistence.FetchType.LAZY;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A node of the SAFAD taxonomy tree: an L1 category (root, {@code parent == null}), an L2 subcategory or an L3
 * subcategory. Archetype products hang off the L3 nodes.
 */
@Entity
@Table(name = "TH_ARCHETYPE_CATEGORY")
public class ArchetypeCategoryEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "parent_id")
	private ArchetypeCategoryEntity parent;

	@Column(name = "level")
	private short level;

	@Column(name = "name")
	private String name;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public ArchetypeCategoryEntity getParent() {
		return parent;
	}

	public void setParent(ArchetypeCategoryEntity parent) {
		this.parent = parent;
	}

	public short getLevel() {
		return level;
	}

	public void setLevel(short level) {
		this.level = level;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
