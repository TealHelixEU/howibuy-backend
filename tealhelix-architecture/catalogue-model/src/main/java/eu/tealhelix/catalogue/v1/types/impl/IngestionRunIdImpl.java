package eu.tealhelix.catalogue.v1.types.impl;

import java.util.Objects;
import java.util.UUID;

import eu.tealhelix.catalogue.v1.types.IngestionRunId;

public final class IngestionRunIdImpl implements IngestionRunId {
	private final String representation;

	public IngestionRunIdImpl(String representation) {
		this.representation = Objects.requireNonNull(representation);
	}

	@Override
	public String asString() {
		return representation;
	}

	@Override
	public UUID asUuid() {
		return UUID.fromString(representation);
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof IngestionRunId other && Objects.equals(representation, other.asString()));
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}

	@Override
	public String toString() {
		return "IngestionRunIdImpl(" + representation + ")";
	}
}
