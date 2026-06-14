package eu.tealhelix.howibuy.v1.types.impl;

import java.util.Objects;
import java.util.UUID;

import eu.tealhelix.howibuy.v1.types.RetailerId;

public class GenericRetailerId implements RetailerId {
	private final String representation;

	public GenericRetailerId(String representation) {
		Objects.requireNonNull(representation);
		this.representation = representation;
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
		if (!(o instanceof RetailerId that)) return false;
		return Objects.equals(asString(), that.asString());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}

	@Override
	public String toString() {
		return "GenericRetailerId(" + representation + ")";
	}
}
