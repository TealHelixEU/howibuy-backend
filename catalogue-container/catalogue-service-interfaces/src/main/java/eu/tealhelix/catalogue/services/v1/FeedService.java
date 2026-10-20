package eu.tealhelix.catalogue.services.v1;

import java.io.InputStream;

import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.common.v1.model.User;

/**
 * Receives the Feeds of the Retailers.
 */
public interface FeedService {
	/**
	 * Store a Feed and record its Ingestion Run as {@link eu.tealhelix.catalogue.v1.types.IngestionRunStatus#RECEIVED
	 * RECEIVED}. The body is read to its end and stored as it arrives, never held in memory whole. This method blocks
	 * until then, so it must be called where blocking is allowed.
	 *
	 * @param caller      The caller, a Retailer
	 * @param catalogueId The Catalogue the Feed is for, an active one of the caller
	 * @param feedType    What the Feed tells about its Catalogue
	 * @param body        The Feed as the Retailer sent it
	 * @return The run
	 * @throws eu.tealhelix.common.types.authorization.NotAuthenticatedException If the caller is not authenticated
	 * @throws eu.tealhelix.common.types.authorization.NotAuthorizedException    If the caller is not a Retailer, or the
	 *                                                                            Catalogue is another Retailer's or inactive
	 * @throws eu.tealhelix.common.types.entity.NotFoundException                If the Catalogue does not exist
	 */
	IngestionRun receive(User caller, CatalogueId catalogueId, FeedType feedType, InputStream body);
}
