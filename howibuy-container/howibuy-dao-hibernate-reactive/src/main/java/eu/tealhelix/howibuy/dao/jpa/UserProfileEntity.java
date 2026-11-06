package eu.tealhelix.howibuy.dao.jpa;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "TH_USER_PROFILE")
public class UserProfileEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "idm_id")
	private String idmId;

	@Column(name = "email")
	private String email;

	@Column(name = "email_consent")
	private Boolean emailConsent;

	/**
	 * Changed only by an update that adds to it in the database, so that additions made at the same time are all counted;
	 * an insert leaves it to the default of the column.
	 */
	@Column(name = "xp_total", insertable = false, updatable = false)
	private int xpTotal;

	/**
	 * Changed only by an update in the database, which adds one to it or resets it, so that baskets registered at the
	 * same time all count; an insert leaves it to the default of the column.
	 */
	@Column(name = "streak", insertable = false, updatable = false)
	private int streak;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getIdmId() {
		return idmId;
	}

	public void setIdmId(String idmId) {
		this.idmId = idmId;
	}

	public Boolean getEmailConsent() {
		return emailConsent;
	}

	public void setEmailConsent(Boolean emailConsent) {
		this.emailConsent = emailConsent;
	}

	public int getXpTotal() {
		return xpTotal;
	}

	public int getStreak() {
		return streak;
	}
}
