package eu.tealhelix.catalogue.dao;

import java.util.List;

import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
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
	 * Store the new state of an Ingestion Run.
	 *
	 * @param tx  The persistence context
	 * @param run The run, which exists
	 * @return Completes when the run is stored
	 */
	Uni<Void> update(ReactivePersistenceTxContext tx, IngestionRun run);

	/**
	 * Read the run of a Catalogue to process next: its oldest {@link IngestionRunStatus#RECEIVED RECEIVED} run, unless one
	 * of its runs is {@link IngestionRunStatus#PROCESSING PROCESSING} or {@link IngestionRunStatus#HELD HELD}, which later
	 * runs wait for.
	 *
	 * @param em          The persistence context
	 * @param catalogueId The Catalogue
	 * @return The run, {@code null} if there is none to process now
	 */
	Uni<IngestionRun> findNextToProcess(ReactivePersistenceContext em, CatalogueId catalogueId);

	/**
	 * Read the Catalogues that have runs {@link IngestionRunStatus#RECEIVED RECEIVED}.
	 *
	 * @param em The persistence context
	 * @return The Catalogues
	 */
	Uni<List<CatalogueId>> findCataloguesWithReceivedRuns(ReactivePersistenceContext em);

	/**
	 * Set every run that is {@link IngestionRunStatus#PROCESSING PROCESSING} back to {@link IngestionRunStatus#RECEIVED
	 * RECEIVED}, so it is processed again.
	 *
	 * @param tx The persistence context
	 * @return How many runs were reset
	 */
	Uni<Integer> resetProcessing(ReactivePersistenceTxContext tx);

	/**
	 * Record the records a run rejected.
	 *
	 * @param tx      The persistence context
	 * @param runId   The run
	 * @param rejects The rejects
	 * @return Completes when the rejects are recorded
	 */
	Uni<Void> createRejects(ReactivePersistenceTxContext tx, IngestionRunId runId, List<IngestionReject> rejects);

	/**
	 * Delete every reject of a run.
	 *
	 * @param tx    The persistence context
	 * @param runId The run
	 * @return Completes when the rejects are deleted
	 */
	Uni<Void> deleteRejects(ReactivePersistenceTxContext tx, IngestionRunId runId);

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
