package eu.tealhelix.catalogue.dao;

import eu.tealhelix.catalogue.v1.model.Feed;
import eu.tealhelix.catalogue.v1.types.HasFeedId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import io.smallrye.mutiny.Uni;

public interface FeedDao {
	/**
	 * Record a Feed that was stored.
	 *
	 * @param tx   The persistence context
	 * @param feed The Feed
	 * @return Completes when the Feed is recorded
	 */
	Uni<Void> create(ReactivePersistenceTxContext tx, Feed feed);

	/**
	 * Read a Feed.
	 *
	 * @param em     The persistence context
	 * @param feedId The Feed
	 * @return The Feed, {@code null} if it does not exist
	 */
	Uni<Feed> find(ReactivePersistenceContext em, HasFeedId feedId);
}
