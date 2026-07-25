package eu.tealhelix.sfc.dao;

import java.util.Optional;
import java.util.UUID;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import io.smallrye.mutiny.Uni;

public interface AttemptDao {
	/**
	 * The id of the user's in-progress attempt, or empty if they have none. At most one exists (enforced at the
	 * database level).
	 */
	Uni<Optional<UUID>> findInProgressId(ReactivePersistenceContext em, UUID userId);

	/**
	 * Starts a fresh in-progress attempt for the user and returns its id.
	 */
	Uni<UUID> startInProgress(ReactivePersistenceTxContext tx, UUID userId);
}
