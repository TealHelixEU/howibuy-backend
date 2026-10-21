package eu.tealhelix.catalogue.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.tealhelix.catalogue.dao.RetailerProductDao;
import eu.tealhelix.catalogue.services.v1.types.ProductChangePage;
import eu.tealhelix.catalogue.v1.model.ImmutableListing;
import eu.tealhelix.catalogue.v1.model.ImmutableProductChange;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.services.authz.impl.TealHelixAuthorizationImpl;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.authorization.NotAuthenticatedException;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.v1.model.Role;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Who may read the Product Changes, which pages are refused, and where the next page is read after, with the database
 * mocked.
 */
@ExtendWith(MockitoExtension.class)
public class ProductChangeServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);
	private static final int DEFAULT_LIMIT = 500;
	private static final int MAX_LIMIT = 1000;

	private static final User CATALOGUE_SYNC_USER = new UserImpl(new UserIdImpl("518cae6a-f2b2-4454-b74d-f2404feab2f5"), null, null, false, false, Set.of(Role.CATALOGUE_SYNC));
	private static final User RETAILER_USER = new UserImpl(new UserIdImpl("6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11"), null, null, false, false, Set.of(Role.RETAILER));
	private static final User OPERATOR_USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, true, Set.of(Role.OPERATOR));

	private static final ProductChange MILK = product("milk", 41);
	private static final ProductChange BREAD = product("bread", 57);

	@Mock
	RetailerProductDao retailerProductDao;

	@RegisterExtension
	private MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	private ProductChangeServiceImpl sut;

	@BeforeEach
	void setUp() {
		sut = new ProductChangeServiceImpl(new TealHelixAuthorizationImpl(), retailerProductDao, persistenceContextFactory, DEFAULT_LIMIT, MAX_LIMIT);
	}

	@Test
	void readsThePageAfterTheValueAndContinuesFromItsLastChange() {
		when(retailerProductDao.findChangesAfter(any(), eq(30L), eq(2))).thenReturn(Uni.createFrom().item(List.of(MILK, BREAD)));

		assertEquals(new ProductChangePage(57, List.of(MILK, BREAD)), sut.findChanges(CATALOGUE_SYNC_USER, 30, 2).await().atMost(WAIT));
	}

	@Test
	void anEmptyPageContinuesFromTheValueItWasReadAfter() {
		when(retailerProductDao.findChangesAfter(any(), eq(57L), eq(2))).thenReturn(Uni.createFrom().item(List.of()));

		assertEquals(new ProductChangePage(57, List.of()), sut.findChanges(CATALOGUE_SYNC_USER, 57, 2).await().atMost(WAIT));
	}

	@Test
	void readsTheDefaultNumberOfChangesWithoutALimit() {
		when(retailerProductDao.findChangesAfter(any(), eq(0L), eq(DEFAULT_LIMIT))).thenReturn(Uni.createFrom().item(List.of(MILK)));

		assertEquals(new ProductChangePage(41, List.of(MILK)), sut.findChanges(CATALOGUE_SYNC_USER, 0, null).await().atMost(WAIT));
	}

	@Test
	void readsAsManyChangesAsTheMaximumLimit() {
		when(retailerProductDao.findChangesAfter(any(), eq(0L), eq(MAX_LIMIT))).thenReturn(Uni.createFrom().item(List.of(MILK)));

		assertEquals(new ProductChangePage(41, List.of(MILK)), sut.findChanges(CATALOGUE_SYNC_USER, 0, MAX_LIMIT).await().atMost(WAIT));
	}

	@Test
	void refusesALimitAboveTheMaximum() {
		assertThrows(BadInputValueException.class, () -> sut.findChanges(CATALOGUE_SYNC_USER, 0, MAX_LIMIT + 1));
		verifyNoInteractions(retailerProductDao);
	}

	@Test
	void refusesALimitBelowOne() {
		assertThrows(BadInputValueException.class, () -> sut.findChanges(CATALOGUE_SYNC_USER, 0, 0));
		verifyNoInteractions(retailerProductDao);
	}

	@Test
	void refusesANegativeValueToReadAfter() {
		assertThrows(BadInputValueException.class, () -> sut.findChanges(CATALOGUE_SYNC_USER, -1, 10));
		verifyNoInteractions(retailerProductDao);
	}

	@Test
	void refusesAnUnauthenticatedCaller() {
		assertThrows(NotAuthenticatedException.class, () -> sut.findChanges(null, 0, 10));
		verifyNoInteractions(retailerProductDao);
	}

	@Test
	void refusesARetailer() {
		assertThrows(NotAuthorizedException.class, () -> sut.findChanges(RETAILER_USER, 0, 10));
		verifyNoInteractions(retailerProductDao);
	}

	@Test
	void refusesAnOperator() {
		assertThrows(NotAuthorizedException.class, () -> sut.findChanges(OPERATOR_USER, 0, 10));
		verifyNoInteractions(retailerProductDao);
	}

	private static ProductChange product(String key, long changeSequence) {
		return ImmutableProductChange.builder()
				.retailerId(new GenericRetailerId("6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11"))
				.productKey(new ProductKeyImpl(key))
				.changeSequence(changeSequence)
				.isRemoved(false)
				.inputHash("a".repeat(64))
				.names(Map.of("el", key))
				.addListings(ImmutableListing.builder().catalogueId(new CatalogueIdImpl("test-gr")).price(new BigDecimal("1.29")).currency(Currency.getInstance("EUR")).isActive(true).build())
				.build();
	}
}
