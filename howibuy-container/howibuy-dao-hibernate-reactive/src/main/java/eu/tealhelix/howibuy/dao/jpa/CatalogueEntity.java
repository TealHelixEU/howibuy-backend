package eu.tealhelix.howibuy.dao.jpa;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "TH_CATALOGUE")
public class CatalogueEntity {
	@Id
	@Column(name = "id")
	private String id;

	@Column(name = "retailer_id")
	private UUID retailerId;

	@Column(name = "country")
	private String country;

	@Column(name = "primary_language")
	private String primaryLanguage;

	@Column(name = "active")
	private boolean active;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public UUID getRetailerId() {
		return retailerId;
	}

	public void setRetailerId(UUID retailerId) {
		this.retailerId = retailerId;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getPrimaryLanguage() {
		return primaryLanguage;
	}

	public void setPrimaryLanguage(String primaryLanguage) {
		this.primaryLanguage = primaryLanguage;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}
}
