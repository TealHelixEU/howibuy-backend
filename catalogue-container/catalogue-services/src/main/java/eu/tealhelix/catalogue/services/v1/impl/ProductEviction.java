package eu.tealhelix.catalogue.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forcm;

import java.time.Duration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.dao.RetailerProductDao;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.services.generic.DateTimeService;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forgets the products that the Retailers stopped selling long ago. A Listing that is inactive and was last seen longer
 * ago than the listing threshold is deleted. A product left without Listings becomes a tombstone, which the change
 * cursor carries to HowiBuy, and a tombstone older than the purge period is deleted. A tombstone that a Feed carries
 * again comes back to life with the same id. The eviction runs in one transaction that holds the change sequence from
 * its start, and the batches of the Feeds hold it before they read their products, so neither changes products the
 * other has read. Running it again at once changes nothing.
 */
@ApplicationScoped
public class ProductEviction {
	public static final String LISTING_THRESHOLD_KEY = "catalogue.eviction.listing-threshold";
	public static final String TOMBSTONE_PURGE_PERIOD_KEY = "catalogue.eviction.tombstone-purge-period";

	private static final Logger LOG = LoggerFactory.getLogger(ProductEviction.class);

	/**
	 * @param deletedListings How many Listings were deleted
	 * @param removedProducts How many products became tombstones
	 * @param purgedProducts  How many tombstones were deleted
	 */
	public record Outcome(int deletedListings, int removedProducts, int purgedProducts) {
	}

	private final RetailerProductDao retailerProductDao;
	private final DateTimeService dateTimeService;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final Duration listingThreshold;
	private final Duration tombstonePurgePeriod;

	@Inject
	public ProductEviction(
			RetailerProductDao retailerProductDao,
			DateTimeService dateTimeService,
			ReactivePersistenceContextFactory persistenceContextFactory,
			@ConfigProperty(name = LISTING_THRESHOLD_KEY) Duration listingThreshold,
			@ConfigProperty(name = TOMBSTONE_PURGE_PERIOD_KEY) Duration tombstonePurgePeriod
	) {
		this.retailerProductDao = retailerProductDao;
		this.dateTimeService = dateTimeService;
		this.persistenceContextFactory = persistenceContextFactory;
		this.listingThreshold = listingThreshold;
		this.tombstonePurgePeriod = tombstonePurgePeriod;
	}

	/**
	 * Evict the long-inactive Listings, make the products left without Listings tombstones and purge the old tombstones.
	 *
	 * @return What was deleted and removed
	 */
	public Uni<Outcome> evict() {
		var now = dateTimeService.getNow();
		return persistenceContextFactory.withTransaction(tx -> forcm(
						retailerProductDao.holdChangeSequence(tx),
						_ -> retailerProductDao.deleteInactiveListings(tx, now.minus(listingThreshold)),
						_ -> retailerProductDao.removeUnlistedProducts(tx, now),
						_ -> retailerProductDao.purgeRemovedProducts(tx, now.minus(tombstonePurgePeriod)),
						(_, deleted, removed, purged) -> new Outcome(deleted, removed, purged)
				))
				.invoke(outcome ->
						LOG.info(
								"Evicted the long-inactive Listings, deleted Listings, new tombstones, purged tombstones: {}, {}, {}",
								outcome.deletedListings(), outcome.removedProducts(), outcome.purgedProducts()
						)
				);
	}
}
