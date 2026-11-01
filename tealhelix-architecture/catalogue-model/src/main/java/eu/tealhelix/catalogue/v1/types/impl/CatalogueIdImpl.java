package eu.tealhelix.catalogue.v1.types.impl;

import java.util.Objects;

import eu.tealhelix.catalogue.v1.types.CatalogueId;

public final class CatalogueIdImpl implements CatalogueId {
	private final String representation;

	public CatalogueIdImpl(String representation) {
		this.representation = Objects.requireNonNull(representation);
	}

	@Override
	public String asString() {
		return representation;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof CatalogueId other && Objects.equals(representation, other.asString()));
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}

	@Override
	public String toString() {
		return "CatalogueIdImpl(" + representation + ")";
	}
}
