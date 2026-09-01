package eu.tealhelix.howibuy.dao.jpa.values;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ArchetypeSubstitutabilityPK implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private UUID fromCategory;
	private UUID toCategory;

	public ArchetypeSubstitutabilityPK() {
		// NOOP
	}

	public ArchetypeSubstitutabilityPK(UUID fromCategory, UUID toCategory) {
		this.fromCategory = fromCategory;
		this.toCategory = toCategory;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ArchetypeSubstitutabilityPK other)) return false;
		return Objects.equals(getFromCategory(), other.getFromCategory()) && Objects.equals(getToCategory(), other.getToCategory());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getFromCategory(), getToCategory());
	}

	public UUID getFromCategory() {
		return fromCategory;
	}

	public void setFromCategory(UUID fromCategory) {
		this.fromCategory = fromCategory;
	}

	public UUID getToCategory() {
		return toCategory;
	}

	public void setToCategory(UUID toCategory) {
		this.toCategory = toCategory;
	}
}
