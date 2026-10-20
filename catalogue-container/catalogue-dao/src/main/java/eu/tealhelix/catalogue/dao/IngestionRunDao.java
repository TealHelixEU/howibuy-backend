package eu.tealhelix.catalogue.dao;

import java.util.List;

import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import io.smallrye.mutiny.Uni;

public interface IngestionRunDao {
	/**
	 * Record a new Ingestion Run.
	 *
	 * @param tx  The persistence context
	 * @param run The run
	 * @return Completes when the run is recorded
	 */
	Uni<Void> create(ReactivePersistenceTxContext tx, IngestionRun run);

	/**
	 * Read an Ingestion Run.
	 *
	 * @param em    The persistence context
	 * @param runId The run
	 * @return The run, {@code null} if it does not exist
	 */
	Uni<IngestionRun> find(ReactivePersistenceContext em, IngestionRunId runId);

	/**
	 * Read a page of the records an Ingestion Run rejected, in the order they appear in its Feed.
	 *
	 * @param em     The persistence context
	 * @param runId  The run
	 * @param offset How many rejects to skip
	 * @param limit  How many rejects to read at most
	 * @return The rejects of the page, empty past the last one
	 */
	Uni<List<IngestionReject>> findRejects(ReactivePersistenceContext em, IngestionRunId runId, int offset, int limit);
}
