package eu.tealhelix.howibuy.dao.jpa.values;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class CorrelationIdPK implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private UUID retailer;
	private String correlationId;

	public CorrelationIdPK() {
		// NOOP
	}

	public CorrelationIdPK(UUID retailer, String correlationId) {
		this.retailer = retailer;
		this.correlationId = correlationId;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof CorrelationIdPK that)) return false;
		return Objects.equals(getRetailer(), that.getRetailer()) && Objects.equals(getCorrelationId(), that.getCorrelationId());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getRetailer(), getCorrelationId());
	}

	public UUID getRetailer() {
		return retailer;
	}

	public void setRetailer(UUID retailer) {
		this.retailer = retailer;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public void setCorrelationId(String correlationId) {
		this.correlationId = correlationId;
	}
}
