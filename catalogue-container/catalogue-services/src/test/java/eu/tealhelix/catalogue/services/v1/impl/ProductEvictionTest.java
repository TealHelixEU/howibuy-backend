package eu.tealhelix.catalogue.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;

import eu.tealhelix.catalogue.dao.RetailerProductDao;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * When the eviction deletes Listings, makes tombstones and purges them, with the database mocked. What it does to the
 * rows is covered by the DAO's tests and the DB-behaviour tests of the deployable.
 */
@ExtendWith(MockitoExtension.class)
public class ProductEvictionTest {
	private static final Duration WAIT = Duration.ofSeconds(300);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 3, 30, 0);
	private static final Duration LISTING_THRESHOLD = Duration.ofDays(90);
	private static final Duration TOMBSTONE_PURGE_PERIOD = Duration.ofDays(365);

	@Mock
	RetailerProductDao retailerProductDao;

	@Mock
	DateTimeService dateTimeService;

	@RegisterExtension
	private MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	private ProductEviction sut;

	@BeforeEach
	void setUp() {
		sut = new ProductEviction(retailerProductDao, dateTimeService, persistenceContextFactory, LISTING_THRESHOLD, TOMBSTONE_PURGE_PERIOD);
		when(dateTimeService.getNow()).thenReturn(NOW);
	}

	/**
	 * The change sequence is held before anything is read, so that no Feed batch applies over what the eviction changes.
	 */
	@Test
	void deletesTheLongInactiveListingsThenMakesTombstonesThenPurgesTheOldOnes() {
		when(retailerProductDao.holdChangeSequence(any())).thenReturn(Uni.createFrom().voidItem());
		when(retailerProductDao.deleteInactiveListings(any(), eq(NOW.minusDays(90)))).thenReturn(Uni.createFrom().item(5));
		when(retailerProductDao.removeUnlistedProducts(any(), eq(NOW))).thenReturn(Uni.createFrom().item(3));
		when(retailerProductDao.purgeRemovedProducts(any(), eq(NOW.minusDays(365)))).thenReturn(Uni.createFrom().item(2));

		assertEquals(new ProductEviction.Outcome(5, 3, 2), sut.evict().await().atMost(WAIT));

		var order = inOrder(retailerProductDao);
		order.verify(retailerProductDao).holdChangeSequence(any());
		order.verify(retailerProductDao).deleteInactiveListings(any(), any());
		order.verify(retailerProductDao).removeUnlistedProducts(any(), any());
		order.verify(retailerProductDao).purgeRemovedProducts(any(), any());
	}
}
