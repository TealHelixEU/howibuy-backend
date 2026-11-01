package eu.tealhelix.howibuy.v1.types.impl;

import java.util.Objects;
import java.util.UUID;

import eu.tealhelix.howibuy.v1.types.RetailerProductId;

public final class RetailerProductIdImpl implements RetailerProductId {
	private final String representation;

	public RetailerProductIdImpl(String representation) {
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
		if (!(o instanceof RetailerProductId that)) return false;
		return Objects.equals(asString(), that.asString());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}

	@Override
	public String toString() {
		return "RetailerProductIdImpl(" + representation + ")";
	}
}
