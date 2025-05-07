package eu.tealhelix.common.web.authentication.jwt;

import java.util.Objects;

import eu.tealhelix.common.v1.types.UserId;

public class UserIdImpl implements UserId {
	private final String representation;

	public UserIdImpl(String representation) {
		this.representation = representation;
	}

	@Override
	public String asString() {
		return representation;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof UserId other && Objects.equals(representation, other.asString()));
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}
}
