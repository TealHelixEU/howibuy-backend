package eu.tealhelix.betterme.dao.jpa;

import static jakarta.persistence.EnumType.STRING;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import eu.tealhelix.betterme.dao.jpa.values.ConsentPK;
import eu.tealhelix.betterme.dao.jpa.values.ConsentState;

@Entity
@Table(name = "THBM_CONSENT")
@IdClass(ConsentPK.class)
public class ConsentEntity {
	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Id
	@Column(name = "retailer_id")
	private UUID retailerId;

	@Enumerated(STRING)
	private ConsentState state;

	public UUID getUserId() {
		return userId;
	}

	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public UUID getRetailerId() {
		return retailerId;
	}

	public void setRetailerId(UUID retailerId) {
		this.retailerId = retailerId;
	}

	public ConsentState getState() {
		return state;
	}

	public void setState(ConsentState state) {
		this.state = state;
	}
}
