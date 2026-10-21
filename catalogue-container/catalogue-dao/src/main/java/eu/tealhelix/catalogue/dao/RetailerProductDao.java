package eu.tealhelix.catalogue.dao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.v1.types.RetailerId;
import io.smallrye.mutiny.Uni;

/**
 * Stores the Retailer Products of the Ingestion, each as the complete current state of the product and its Listings.
 */
public interface RetailerProductDao {
	/**
	 * Read the current state of some products of a Retailer.
	 *
	 * @param em          The persistence context
	 * @param retailerId  The Retailer
	 * @param productKeys The keys of the products
	 * @return The products found, by key; the keys of products never stored are missing from it
	 */
	Uni<Map<ProductKey, ProductChange>> findProducts(ReactivePersistenceContext em, RetailerId retailerId, Collection<ProductKey> productKeys);

	/**
	 * Take values of the global change sequence, which grows with every value taken.
	 *
	 * @param tx    The persistence context
	 * @param count How many values to take
	 * @return The values, in ascending order
	 */
	Uni<List<Long>> takeChangeSequences(ReactivePersistenceTxContext tx, int count);

	/**
	 * Store the state of products, creating the products never stored. The Listings of the given Catalogue are recorded
	 * as seen by the given run.
	 *
	 * @param tx          The persistence context
	 * @param catalogueId The Catalogue of the Feed that carried the products
	 * @param runId       The Ingestion Run of the Feed
	 * @param seenAt      When the Feed carried them
	 * @param products    The complete state of each product, its names, characteristics, tags and Listings replacing the
	 *                    stored ones; all of them of the Retailer of the Catalogue
	 * @return Completes when the products are stored
	 */
	Uni<Void> save(ReactivePersistenceTxContext tx, CatalogueId catalogueId, IngestionRunId runId, LocalDateTime seenAt, Collection<ProductChange> products);
}
