package eu.tealhelix.betterme.dao.jpa.values;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ConsentPK implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private UUID userId;
	private UUID retailerId;

	public ConsentPK() {
		// NOOP
	}

	public ConsentPK(UUID userId, UUID retailerId) {
		this.userId = userId;
		this.retailerId = retailerId;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ConsentPK consentPK)) return false;
		return Objects.equals(getUserId(), consentPK.getUserId()) && Objects.equals(getRetailerId(), consentPK.getRetailerId());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getUserId(), getRetailerId());
	}

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
}
