package eu.tealhelix.howibuy.dao.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "TH_CATALOGUE_CHANGE_CURSOR")
public class CatalogueChangeCursorEntity {
	@Id
	@Column(name = "source")
	private String source;

	@Column(name = "last_sequence")
	private long lastSequence;

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public long getLastSequence() {
		return lastSequence;
	}

	public void setLastSequence(long lastSequence) {
		this.lastSequence = lastSequence;
	}
}
