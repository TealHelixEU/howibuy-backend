package eu.tealhelix.howibuy.dao;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import io.smallrye.mutiny.Uni;

/**
 * The cursor of the Product Changes of the Ingestion: the change sequence up to which they have been applied.
 */
public interface CatalogueChangeCursorDao {
	/**
	 * Read the change sequence up to which the changes have been applied.
	 *
	 * @param em The persistence context
	 * @return The last sequence applied, 0 if none has been
	 */
	Uni<Long> requireLastSequence(ReactivePersistenceContext em);

	/**
	 * Move the cursor from one sequence to another, provided it still is at the first one.
	 *
	 * @param tx           The persistence context
	 * @param fromSequence The sequence the cursor has to be at
	 * @param toSequence   The sequence to move it to
	 * @return Whether the cursor was at {@code fromSequence} and has been moved
	 */
	Uni<Boolean> advance(ReactivePersistenceTxContext tx, long fromSequence, long toSequence);
}
