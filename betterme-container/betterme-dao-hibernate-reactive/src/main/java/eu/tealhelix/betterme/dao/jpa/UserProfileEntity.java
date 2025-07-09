package eu.tealhelix.betterme.dao.jpa;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "THBM_USER_PROFILE")
public class UserProfileEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "external_id")
	private String externalId;

	@Column(name = "email")
	private String email;

	@Column(name = "email_consent")
	private Boolean emailConsent;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getExternalId() {
		return externalId;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Boolean getEmailConsent() {
		return emailConsent;
	}

	public void setEmailConsent(Boolean emailConsent) {
		this.emailConsent = emailConsent;
	}
}
