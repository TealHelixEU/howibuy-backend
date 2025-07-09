package eu.tealhelix.betterme.v1.types.impl;

import java.util.Objects;
import java.util.UUID;

import eu.tealhelix.betterme.v1.types.RetailerId;

public class GenericRetailerId implements RetailerId {
	private final String representation;

	public GenericRetailerId(String representation) {
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
}
