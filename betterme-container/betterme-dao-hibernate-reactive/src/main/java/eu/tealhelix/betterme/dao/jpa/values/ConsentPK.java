package eu.tealhelix.betterme.dao.jpa.values;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ConsentPK implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private UUID user;
	private UUID retailer;

	public ConsentPK() {
		// NOOP
	}

	public ConsentPK(UUID user, UUID retailer) {
		this.user = user;
		this.retailer = retailer;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ConsentPK consentPK)) return false;
		return Objects.equals(getUser(), consentPK.getUser()) && Objects.equals(getRetailer(), consentPK.getRetailer());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getUser(), getRetailer());
	}

	public UUID getUser() {
		return user;
	}

	public void setUser(UUID user) {
		this.user = user;
	}

	public UUID getRetailer() {
		return retailer;
	}

	public void setRetailer(UUID retailer) {
		this.retailer = retailer;
	}
}
