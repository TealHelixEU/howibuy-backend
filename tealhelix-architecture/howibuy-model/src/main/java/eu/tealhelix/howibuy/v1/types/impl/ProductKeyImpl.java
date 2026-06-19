package eu.tealhelix.howibuy.v1.types.impl;

import java.util.Objects;

import eu.tealhelix.howibuy.v1.types.ProductKey;

public class ProductKeyImpl implements ProductKey {
	private final String representation;

	public ProductKeyImpl(String representation) {
		this.representation = Objects.requireNonNull(representation);
	}

	@Override
	public String asString() {
		return representation;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ProductKey that)) return false;
		return Objects.equals(asString(), that.asString());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}

	@Override
	public String toString() {
		return "ProductKeyImpl(" + representation + ")";
	}
}
