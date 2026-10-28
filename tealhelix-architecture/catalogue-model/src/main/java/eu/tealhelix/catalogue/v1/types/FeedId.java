package eu.tealhelix.catalogue.v1.types;

import java.util.UUID;

import eu.tealhelix.common.types.RepresentableAsString;

/**
 * Identifies one Feed as received, stored once whatever the number of Catalogues it is for.
 */
public interface FeedId extends HasFeedId, RepresentableAsString {
	@Override
	default FeedId getId() {
		return this;
	}

	UUID asUuid();
}
