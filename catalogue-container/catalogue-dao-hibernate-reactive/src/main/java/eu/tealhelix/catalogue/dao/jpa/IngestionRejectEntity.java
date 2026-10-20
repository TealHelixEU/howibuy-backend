package eu.tealhelix.catalogue.dao.jpa;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import eu.tealhelix.catalogue.dao.jpa.values.IngestionRejectPK;

@Entity
@Table(name = "IN_INGESTION_REJECT")
@IdClass(IngestionRejectPK.class)
public class IngestionRejectEntity {
	@Id
	@Column(name = "ingestion_run_id")
	private UUID ingestionRunId;

	@Id
	@Column(name = "position")
	private long position;

	@Column(name = "product_key")
	private String productKey;

	@Column(name = "reason", length = Integer.MAX_VALUE)
	private String reason;

	public UUID getIngestionRunId() {
		return ingestionRunId;
	}

	public void setIngestionRunId(UUID ingestionRunId) {
		this.ingestionRunId = ingestionRunId;
	}

	public long getPosition() {
		return position;
	}

	public void setPosition(long position) {
		this.position = position;
	}

	public String getProductKey() {
		return productKey;
	}

	public void setProductKey(String productKey) {
		this.productKey = productKey;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}
}
