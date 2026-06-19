package eu.tealhelix.common.types.impl;

import java.util.Objects;

import eu.tealhelix.common.types.EmailAddress;

/**
 * Default implementation of the {@link EmailAddress}.
 */
public class EmailAddressImpl implements EmailAddress {
	private final String representation;

	public EmailAddressImpl(String representation) {
		this.representation = representation;
	}

	@Override
	public String asString() {
		return representation;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof EmailAddress email)) return false;
		return Objects.equals(asString(), email.asString());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}
}
