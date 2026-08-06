package eu.tealhelix.sfc.dao;

import java.time.LocalDateTime;
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
	 * The completion time of the user's most recently completed attempt, or empty if they have never completed one.
	 * Used to decide, on read, whether the stability window has elapsed and a new attempt may start.
	 */
	Uni<Optional<LocalDateTime>> findLatestCompletedAt(ReactivePersistenceContext em, UUID userId);

	/**
	 * The id of the user's most recently completed attempt, or empty if they have never completed one. Used to read the
	 * answers frozen on it once the user has no attempt in progress.
	 */
	Uni<Optional<UUID>> findLatestCompletedId(ReactivePersistenceContext em, UUID userId);

	/**
	 * Starts a fresh in-progress attempt for the user and returns its id.
	 */
	Uni<UUID> startInProgress(ReactivePersistenceTxContext tx, UUID userId);

	/**
	 * Freezes an in-progress attempt: sets its status to completed and stamps the given completion time. The attempt is
	 * an immutable record thereafter.
	 */
	Uni<Void> complete(ReactivePersistenceTxContext tx, UUID attemptId, LocalDateTime completedAt);
}
