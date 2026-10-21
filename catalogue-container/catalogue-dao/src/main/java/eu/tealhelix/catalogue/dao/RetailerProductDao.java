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
	 * Read the products that changed after a value of the change sequence, each in its current state.
	 *
	 * @param em    The persistence context
	 * @param after The value of the change sequence
	 * @param limit How many products to read at most
	 * @return The products whose change sequence is greater than {@code after}, the lowest first
	 */
	Uni<List<ProductChange>> findChangesAfter(ReactivePersistenceContext em, long after, int limit);

	/**
	 * Take values of the global change sequence, which grows with every value taken. The transaction holds the change
	 * sequence until it ends, so that the values become visible in the order they were taken; the other transactions
	 * that take values, or stamp products, wait for it.
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

	/**
	 * Count the active Listings of a Catalogue.
	 *
	 * @param em          The persistence context
	 * @param catalogueId The Catalogue
	 * @return How many Listings of the Catalogue are active
	 */
	Uni<Long> countActiveListings(ReactivePersistenceContext em, CatalogueId catalogueId);

	/**
	 * Count the active Listings of a Catalogue that an Ingestion Run of it did not see.
	 *
	 * @param em          The persistence context
	 * @param catalogueId The Catalogue
	 * @param runId       The run
	 * @return How many active Listings of the Catalogue the run did not see
	 */
	Uni<Long> countUnseenListings(ReactivePersistenceContext em, CatalogueId catalogueId, IngestionRunId runId);

	/**
	 * Make the active Listings of a Catalogue that an Ingestion Run of it did not see inactive, and stamp their products
	 * with new values of the change sequence, which the transaction holds as for
	 * {@link #takeChangeSequences(ReactivePersistenceTxContext, int)}. When the Listings were last seen stays as it was.
	 *
	 * @param tx          The persistence context, which holds none of the products
	 * @param catalogueId The Catalogue
	 * @param runId       The run
	 * @return How many Listings were made inactive
	 */
	Uni<Integer> deactivateUnseenListings(ReactivePersistenceTxContext tx, CatalogueId catalogueId, IngestionRunId runId);

	/**
	 * Make the active Listings of some products in a Catalogue inactive, and stamp the products with new values of the
	 * change sequence, which the transaction holds as for {@link #takeChangeSequences(ReactivePersistenceTxContext, int)}.
	 * When the Listings were last seen stays as it was.
	 *
	 * @param tx          The persistence context, whose copies of the products keep their earlier state
	 * @param retailerId  The Retailer of the Catalogue
	 * @param catalogueId The Catalogue
	 * @param productKeys The keys of the products; those without an active Listing in the Catalogue are left alone
	 * @return How many Listings were made inactive
	 */
	Uni<Integer> deactivateListings(ReactivePersistenceTxContext tx, RetailerId retailerId, CatalogueId catalogueId, Collection<ProductKey> productKeys);

	/**
	 * Hold the change sequence until the transaction ends, as for
	 * {@link #takeChangeSequences(ReactivePersistenceTxContext, int)}, before the transaction reads the products it changes.
	 * The other transactions that change products then cannot change them in between.
	 *
	 * @param tx The persistence context
	 * @return Completes when the transaction holds the change sequence
	 */
	Uni<Void> holdChangeSequence(ReactivePersistenceTxContext tx);

	/**
	 * Delete the inactive Listings that were last seen before a time, of every Catalogue.
	 *
	 * @param tx             The persistence context, which holds none of the products
	 * @param lastSeenBefore The time
	 * @return How many Listings were deleted
	 */
	Uni<Integer> deleteInactiveListings(ReactivePersistenceTxContext tx, LocalDateTime lastSeenBefore);

	/**
	 * Make the products left without Listings tombstones: forget their names, and stamp them with new values of the change
	 * sequence, which the transaction holds as for {@link #takeChangeSequences(ReactivePersistenceTxContext, int)}. The
	 * tombstones stay as they were.
	 *
	 * @param tx        The persistence context, which holds none of the products
	 * @param removedAt When the products became tombstones
	 * @return How many products became tombstones
	 */
	Uni<Integer> removeUnlistedProducts(ReactivePersistenceTxContext tx, LocalDateTime removedAt);

	/**
	 * Delete the tombstones that were made before a time.
	 *
	 * @param tx            The persistence context, which holds none of the products
	 * @param removedBefore The time
	 * @return How many tombstones were deleted
	 */
	Uni<Integer> purgeRemovedProducts(ReactivePersistenceTxContext tx, LocalDateTime removedBefore);
}
