package eu.tealhelix.catalogue.dao.jpa;

import java.util.List;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "IN_CATALOGUE")
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

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "accepted_languages")
	private List<String> acceptedLanguages;

	@Column(name = "adapter")
	private String adapter;

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

	public List<String> getAcceptedLanguages() {
		return acceptedLanguages;
	}

	public void setAcceptedLanguages(List<String> acceptedLanguages) {
		this.acceptedLanguages = acceptedLanguages;
	}

	public String getAdapter() {
		return adapter;
	}

	public void setAdapter(String adapter) {
		this.adapter = adapter;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}
}
