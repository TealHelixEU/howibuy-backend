package eu.tealhelix.catalogue.dao.jpa.values;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class IngestionRejectPK implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private UUID ingestionRunId;
	private long position;

	public IngestionRejectPK() {
		// NOOP
	}

	public IngestionRejectPK(UUID ingestionRunId, long position) {
		this.ingestionRunId = ingestionRunId;
		this.position = position;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof IngestionRejectPK that)) return false;
		return Objects.equals(getIngestionRunId(), that.getIngestionRunId()) && getPosition() == that.getPosition();
	}

	@Override
	public int hashCode() {
		return Objects.hash(getIngestionRunId(), getPosition());
	}

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
}
