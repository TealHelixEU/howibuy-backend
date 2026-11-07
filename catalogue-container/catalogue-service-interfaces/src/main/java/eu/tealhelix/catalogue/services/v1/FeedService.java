package eu.tealhelix.catalogue.services.v1;

import java.io.InputStream;

import eu.tealhelix.catalogue.services.v1.types.FeedWithRuns;
import eu.tealhelix.catalogue.v1.types.HasCatalogueId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.HasFeedId;
import eu.tealhelix.common.v1.model.User;
import io.smallrye.mutiny.Uni;

/**
 * Receives the Feeds of the Retailers.
 */
public interface FeedService {
	/**
	 * Store a Feed for one Catalogue and record its Ingestion Run as {@link
	 * eu.tealhelix.catalogue.v1.types.IngestionRunStatus#RECEIVED RECEIVED}. The body is read to its end and stored as it
	 * arrives, never held in memory whole. This method blocks until then, so it must be called where blocking is
	 * allowed. The run is processed in the background, after the runs of the Catalogue received before it.
	 *
	 * @param caller      The caller, a Retailer
	 * @param catalogueId The Catalogue the Feed is for, an active one of the caller
	 * @param feedType    What the Feed tells about its Catalogue
	 * @param body        The Feed as the Retailer sent it
	 * @return The Feed and its run
	 * @throws eu.tealhelix.common.types.authorization.NotAuthenticatedException  If the caller is not authenticated
	 * @throws eu.tealhelix.common.types.authorization.NotAuthorizedException     If the caller is not a Retailer, or the
	 *                                                                             Catalogue is another Retailer's or inactive
	 * @throws eu.tealhelix.common.types.entity.NotFoundException                 If the Catalogue does not exist
	 * @throws eu.tealhelix.common.types.validation.RequiredInputMissingException If the type is missing
	 */
	FeedWithRuns receive(User caller, HasCatalogueId catalogueId, FeedType feedType, InputStream body);

	/**
	 * Store a Feed once for every active Catalogue of the caller, and record one Ingestion Run per Catalogue as {@link
	 * eu.tealhelix.catalogue.v1.types.IngestionRunStatus#RECEIVED RECEIVED}. It reads and stores the body as {@link
	 * #receive(User, HasCatalogueId, FeedType, InputStream)} does, and each run is processed as that one's is.
	 *
	 * @param caller   The caller, a Retailer with at least one active Catalogue
	 * @param feedType What the Feed tells about each Catalogue
	 * @param body     The Feed as the Retailer sent it
	 * @return The Feed and its runs
	 * @throws eu.tealhelix.common.types.authorization.NotAuthenticatedException  If the caller is not authenticated
	 * @throws eu.tealhelix.common.types.authorization.NotAuthorizedException     If the caller is not a Retailer, or has
	 *                                                                             no active Catalogue
	 * @throws eu.tealhelix.common.types.validation.RequiredInputMissingException If the type is missing
	 */
	FeedWithRuns receiveForActiveCatalogues(User caller, FeedType feedType, InputStream body);

	/**
	 * Read a Feed and its Ingestion Runs. An operator reads every Feed; a Retailer reads the Feeds it sent.
	 *
	 * @param caller The caller
	 * @param feedId The Feed
	 * @return The Feed and its runs
	 * @throws eu.tealhelix.common.types.authorization.NotAuthenticatedException If the caller is not authenticated
	 * @throws eu.tealhelix.common.types.authorization.NotAuthorizedException    If the caller may not read the Feed
	 * @throws eu.tealhelix.common.types.entity.NotFoundException                If the Feed does not exist
	 */
	Uni<FeedWithRuns> find(User caller, HasFeedId feedId);
}
