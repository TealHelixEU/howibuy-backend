package eu.tealhelix.common.types.impl;

import java.util.Objects;

import eu.tealhelix.common.types.Email;

/**
 * Default implementation of the {@link Email}.
 */
public class EmailImpl implements Email {
	private final String representation;

	public EmailImpl(String representation) {
		this.representation = representation;
	}

	@Override
	public String asString() {
		return representation;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Email email)) return false;
		return Objects.equals(asString(), email.asString());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}
}
