package eu.tealhelix.sfc.v1.types.impl;

import java.util.Objects;
import java.util.UUID;

import eu.tealhelix.sfc.v1.types.CategoryId;

public class CategoryIdImpl implements CategoryId {
	private final String representation;

	public CategoryIdImpl(String representation) {
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
		if (!(o instanceof CategoryId that)) return false;
		return Objects.equals(asString(), that.asString());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}

	@Override
	public String toString() {
		return "CategoryIdImpl(" + representation + ")";
	}
}
