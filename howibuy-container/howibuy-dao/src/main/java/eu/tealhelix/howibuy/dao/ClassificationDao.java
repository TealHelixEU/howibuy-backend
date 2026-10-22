package eu.tealhelix.howibuy.dao;

import java.util.List;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.howibuy.v1.model.PendingProduct;
import io.smallrye.mutiny.Uni;

public interface ClassificationDao {
	/**
	 * Find the products waiting to be classified: those without a Classification, and those whose Classification was
	 * resolved from another input hash than their current one.
	 *
	 * @param em    The persistence context
	 * @param limit The most products to find
	 * @return The products, the one whose current state came with the oldest Product Change first
	 */
	Uni<List<PendingProduct>> findPending(ReactivePersistenceContext em, int limit);
}
