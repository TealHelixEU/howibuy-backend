package eu.tealhelix.catalogue.services.v1;

import java.util.List;

import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
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
	Uni<IngestionRun> find(User caller, IngestionRunId runId);

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
	Uni<List<IngestionReject>> findRejects(User caller, IngestionRunId runId, int offset, int limit);

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
	Uni<IngestionRun> confirm(User caller, IngestionRunId runId);

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
	Uni<IngestionRun> discard(User caller, IngestionRunId runId);
}
