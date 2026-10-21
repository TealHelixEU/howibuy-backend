package eu.tealhelix.catalogue.services.v1;

import java.util.List;

import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.HasIngestionRunId;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.common.v1.model.User;
import io.smallrye.mutiny.Uni;

/**
 * Tells the Retailers and the operators how their Ingestion Runs went, and lets the operators decide on the held ones. A
 * run can be read by the Retailer of its Catalogue and by the operators.
 */
public interface IngestionRunService {
	/**
	 * The most rejects that {@link #findRejects(User, IngestionRunId, int, int)} reads at once.
	 */
	int MAX_REJECTS_PER_PAGE = 1000;

	/**
	 * Read an Ingestion Run.
	 *
	 * @param caller The caller
	 * @param runId  The run
	 * @return The run
	 * @throws eu.tealhelix.common.types.authorization.NotAuthenticatedException If the caller is not authenticated
	 * @throws eu.tealhelix.common.types.authorization.NotAuthorizedException    If the caller may not read the run
	 * @throws eu.tealhelix.common.types.entity.NotFoundException                If the run does not exist
	 */
	Uni<IngestionRun> find(User caller, HasIngestionRunId runId);

	/**
	 * Read a page of the records an Ingestion Run rejected, in the order they appear in its Feed.
	 *
	 * @param caller The caller
	 * @param runId  The run
	 * @param offset How many rejects to skip, not negative
	 * @param limit  How many rejects to read at most, from 1 to {@link #MAX_REJECTS_PER_PAGE}
	 * @return The rejects of the page, empty past the last one
	 * @throws eu.tealhelix.common.types.authorization.NotAuthenticatedException If the caller is not authenticated
	 * @throws eu.tealhelix.common.types.authorization.NotAuthorizedException    If the caller may not read the run
	 * @throws eu.tealhelix.common.types.entity.NotFoundException                If the run does not exist
	 * @throws eu.tealhelix.common.types.validation.BadInputValueException       If the offset or the limit is out of range
	 */
	Uni<List<IngestionReject>> findRejects(User caller, HasIngestionRunId runId, int offset, int limit);

	/**
	 * Confirm a {@link eu.tealhelix.catalogue.v1.types.IngestionRunStatus#HELD HELD} Snapshot: take the active Listings of
	 * its Catalogue that it did not carry off sale, and set it {@link eu.tealhelix.catalogue.v1.types.IngestionRunStatus#DONE
	 * DONE}. The runs of the Catalogue that waited for it are processed next.
	 *
	 * @param caller The caller, an operator
	 * @param runId  The run
	 * @return The run, done
	 * @throws eu.tealhelix.common.types.authorization.NotAuthenticatedException       If the caller is not authenticated
	 * @throws eu.tealhelix.common.types.authorization.NotAuthorizedException          If the caller is not an operator
	 * @throws eu.tealhelix.common.types.entity.NotFoundException                      If the run does not exist
	 * @throws eu.tealhelix.catalogue.services.v1.types.IngestionRunNotHeldException If the run is not held
	 */
	Uni<IngestionRun> confirm(User caller, HasIngestionRunId runId);

	/**
	 * Discard a {@link eu.tealhelix.catalogue.v1.types.IngestionRunStatus#HELD HELD} Snapshot: set it
	 * {@link eu.tealhelix.catalogue.v1.types.IngestionRunStatus#DISCARDED DISCARDED}, taking nothing off sale. The products
	 * it applied before it was held stay as they are. The runs of the Catalogue that waited for it are processed next.
	 *
	 * @param caller The caller, an operator
	 * @param runId  The run
	 * @return The run, discarded
	 * @throws eu.tealhelix.common.types.authorization.NotAuthenticatedException       If the caller is not authenticated
	 * @throws eu.tealhelix.common.types.authorization.NotAuthorizedException          If the caller is not an operator
	 * @throws eu.tealhelix.common.types.entity.NotFoundException                      If the run does not exist
	 * @throws eu.tealhelix.catalogue.services.v1.types.IngestionRunNotHeldException If the run is not held
	 */
	Uni<IngestionRun> discard(User caller, HasIngestionRunId runId);

	/**
	 * Replay a finished Ingestion Run: record a new run that processes its stored Feed again, with the adapter the
	 * Catalogue has now, e.g. after a fix of the adapter. The new run shares the Feed's file and receipt, and waits its
	 * turn among the runs of the Catalogue in order of receipt. The run replayed stays as it is.
	 * <p>
	 * A Snapshot is not replayed once a later Snapshot of its Catalogue has been received and has not failed, because
	 * its replay would take off sale the Listings that the later one carries. Any other replay out of order is the
	 * operator's to repair: replaying the later Feeds of the Catalogue as well, in order, brings back what they did.
	 *
	 * @param caller The caller, an operator
	 * @param runId  The run, {@link eu.tealhelix.catalogue.v1.types.IngestionRunStatus#DONE DONE},
	 *               {@link eu.tealhelix.catalogue.v1.types.IngestionRunStatus#FAILED FAILED} or
	 *               {@link eu.tealhelix.catalogue.v1.types.IngestionRunStatus#DISCARDED DISCARDED}
	 * @return The new run, {@link eu.tealhelix.catalogue.v1.types.IngestionRunStatus#RECEIVED RECEIVED}
	 * @throws eu.tealhelix.common.types.authorization.NotAuthenticatedException             If the caller is not authenticated
	 * @throws eu.tealhelix.common.types.authorization.NotAuthorizedException                If the caller is not an operator
	 * @throws eu.tealhelix.common.types.entity.NotFoundException                            If the run does not exist, or its Feed is no longer stored
	 * @throws eu.tealhelix.catalogue.services.v1.types.IngestionRunNotReplayableException If the run has not finished, or a later Snapshot supersedes it
	 */
	Uni<IngestionRun> replay(User caller, HasIngestionRunId runId);
}
