package eu.tealhelix.common.v1.types;

import java.util.UUID;

import eu.tealhelix.common.types.RepresentableAsString;

/**
 * Abstract user id.
 */
public interface UserId extends HasUserId, RepresentableAsString {
	@Override
	default UserId getId() {
		return this;
	}

	UUID asUuid();
}
