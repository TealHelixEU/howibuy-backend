package eu.tealhelix.howibuy.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.model.ImmutableListing;
import eu.tealhelix.catalogue.v1.model.ImmutableProductChange;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.services.authz.impl.TealHelixAuthorizationImpl;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.authorization.NotAuthenticatedException;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.model.Role;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.CatalogueChangeCursorDao;
import eu.tealhelix.howibuy.dao.CatalogueDao;
import eu.tealhelix.howibuy.dao.RetailerDao;
import eu.tealhelix.howibuy.dao.RetailerProductDao;
import eu.tealhelix.howibuy.services.v1.types.ChangeCursorConflictException;
import eu.tealhelix.howibuy.services.v1.types.ProductChangePage;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Which callers may sync, which pages are refused before anything is stored, and the order of the steps of applying a
 * page, with the database mocked. What a page does to the stored products is covered by {@code RetailerProductDaoImplTest},
 * and the cursor's refusal of a concurrent sync by {@code CatalogueChangeCursorDaoImplTest}.
 */
@EnableAutoWeld
@AddBeanClasses(TealHelixAuthorizationImpl.class)
@ExtendWith(MockitoExtension.class)
public class CatalogueChangeServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final User CATALOGUE_SYNC_USER = new UserImpl(new UserIdImpl("518cae6a-f2b2-4454-b74d-f2404feab2f5"), null, null, false, false, Set.of(Role.CATALOGUE_SYNC));
	private static final User RETAILER_USER = new UserImpl(new UserIdImpl("518cae6a-f2b2-4454-b74d-f2404feab2f5"), null, null, false, false, Set.of(Role.RETAILER));
	private static final User OPERATOR_USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, true, Set.of(Role.OPERATOR));

	private static final RetailerId RETAILER = new GenericRetailerId("6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11");
	private static final RetailerId OTHER_RETAILER = new GenericRetailerId("0b6a2f4e-35c4-4d8e-a1a7-5e2c9f8d7b22");
	private static final CatalogueId CATALOGUE_GR = new CatalogueIdImpl("test-gr");
	private static final CatalogueId CATALOGUE_CY = new CatalogueIdImpl("test-cy");
	private static final CatalogueId OTHER_RETAILERS_CATALOGUE = new CatalogueIdImpl("other-lt");

	@Produces
	@Mock
	CatalogueChangeCursorDao mockCursorDao;

	@Produces
	@Mock
	CatalogueDao mockCatalogueDao;

	@Produces
	@Mock
	RetailerDao mockRetailerDao;

	@Produces
	@Mock
	RetailerProductDao mockRetailerProductDao;

	@Produces
	@RegisterExtension
	private MockReactivePersistenceContextFactory mockReactivePersistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Inject
	CatalogueChangeServiceImpl sut;

	@Test
	void theCatalogueSyncReadsTheCursorOutsideATransaction() {
		when(mockCursorDao.requireLastSequence(any())).thenReturn(Uni.createFrom().item(42L));

		var lastSequence = sut.readCursor(CATALOGUE_SYNC_USER).await().atMost(WAIT);

		assertEquals(42L, lastSequence);
		assertTrue(mockReactivePersistenceContextFactory.getOpenedTransactions().isEmpty(), "no transaction opened");
	}

	@Test
	void onlyTheCatalogueSyncReadsTheCursor() {
		assertThrows(NotAuthenticatedException.class, () -> sut.readCursor(null));
		assertThrows(NotAuthorizedException.class, () -> sut.readCursor(RETAILER_USER));
		assertThrows(NotAuthorizedException.class, () -> sut.readCursor(OPERATOR_USER));
		verifyNoInteractions(mockCursorDao);
	}

	@Test
	void onlyTheCatalogueSyncAppliesAPage() {
		var page = new ProductChangePage(0L, 1L, List.of(change(RETAILER, "yoghurt", 1, CATALOGUE_GR)));

		assertThrows(NotAuthenticatedException.class, () -> sut.apply(null, page));
		assertThrows(NotAuthorizedException.class, () -> sut.apply(RETAILER_USER, page));
		assertThrows(NotAuthorizedException.class, () -> sut.apply(OPERATOR_USER, page));
		assertNothingTouched();
	}

	@Test
	void aPageIsAppliedInOneTransactionAfterTheCursorMovesAndItsRetailersAndCataloguesAreChecked() {
		var changes = List.of(
				change(RETAILER, "yoghurt", 6, CATALOGUE_GR, CATALOGUE_CY),
				change(RETAILER, "feta", 7, CATALOGUE_GR),
				change(OTHER_RETAILER, "jogurtas", 9, OTHER_RETAILERS_CATALOGUE)
		);
		givenTheCursorAdvances(5, 9);
		givenTheRetailersExist(RETAILER, OTHER_RETAILER);
		givenTheCataloguesBelongTo(Map.of(CATALOGUE_GR, RETAILER, CATALOGUE_CY, RETAILER, OTHER_RETAILERS_CATALOGUE, OTHER_RETAILER));
		when(mockRetailerProductDao.apply(any(), eq(changes))).thenReturn(Uni.createFrom().voidItem());

		sut.apply(CATALOGUE_SYNC_USER, new ProductChangePage(5L, 9L, changes)).await().atMost(WAIT);

		assertEquals(1, mockReactivePersistenceContextFactory.getOpenedTransactions().size(), "one transaction");
		var tx = mockReactivePersistenceContextFactory.getOpenedTransactions().getFirst();
		var inOrder = inOrder(mockCursorDao, mockRetailerDao, mockCatalogueDao, mockRetailerProductDao);
		inOrder.verify(mockCursorDao).advance(tx, 5, 9);
		inOrder.verify(mockRetailerDao).findActiveFlag(tx, RETAILER);
		inOrder.verify(mockRetailerDao).findActiveFlag(tx, OTHER_RETAILER);
		inOrder.verify(mockCatalogueDao).findRetailers(tx, Set.of(CATALOGUE_GR, CATALOGUE_CY, OTHER_RETAILERS_CATALOGUE));
		inOrder.verify(mockRetailerProductDao).apply(tx, changes);
		inOrder.verifyNoMoreInteractions();
	}

	@Test
	void aPageNotStartingAtTheCursorIsAConflict() {
		when(mockCursorDao.advance(any(), eq(5L), eq(6L))).thenReturn(Uni.createFrom().item(false));

		var failure = applyAndExpectFailure(new ProductChangePage(5L, 6L, List.of(change(RETAILER, "yoghurt", 6, CATALOGUE_GR))));

		assertInstanceOf(ChangeCursorConflictException.class, failure);
		assertEquals("The page does not start at the change cursor, fromSequence: 5", failure.getMessage());
		verifyNoInteractions(mockRetailerDao, mockCatalogueDao, mockRetailerProductDao);
	}

	@Test
	void anInactiveRetailerIsKnown() {
		var changes = List.of(change(RETAILER, "yoghurt", 6, CATALOGUE_GR));
		givenTheCursorAdvances(5, 6);
		when(mockRetailerDao.findActiveFlag(any(), eq(RETAILER))).thenReturn(Uni.createFrom().item(false));
		givenTheCataloguesBelongTo(Map.of(CATALOGUE_GR, RETAILER));
		when(mockRetailerProductDao.apply(any(), eq(changes))).thenReturn(Uni.createFrom().voidItem());

		sut.apply(CATALOGUE_SYNC_USER, new ProductChangePage(5L, 6L, changes)).await().atMost(WAIT);

		verify(mockRetailerProductDao).apply(any(), eq(changes));
	}

	@Test
	void aPageWithAnUnknownRetailerIsRefused() {
		givenTheCursorAdvances(5, 6);
		when(mockRetailerDao.findActiveFlag(any(), eq(RETAILER))).thenReturn(Uni.createFrom().nullItem());

		var failure = applyAndExpectFailure(new ProductChangePage(5L, 6L, List.of(change(RETAILER, "yoghurt", 6, CATALOGUE_GR))));

		assertBadInput("retailerId", "No such Retailer, retailerId: " + RETAILER.asString(), failure);
		verifyNoInteractions(mockCatalogueDao, mockRetailerProductDao);
	}

	@Test
	void aPageWithAnUnknownCatalogueIsRefused() {
		givenTheCursorAdvances(5, 6);
		givenTheRetailersExist(RETAILER);
		givenTheCataloguesBelongTo(Map.of(CATALOGUE_GR, RETAILER));

		var failure = applyAndExpectFailure(new ProductChangePage(5L, 6L, List.of(change(RETAILER, "yoghurt", 6, CATALOGUE_GR, CATALOGUE_CY))));

		assertBadInput("catalogueId", "No such Catalogue, catalogueId: " + CATALOGUE_CY.asString(), failure);
		verifyNoInteractions(mockRetailerProductDao);
	}

	@Test
	void aPageListingAProductInAnotherRetailersCatalogueIsRefused() {
		givenTheCursorAdvances(5, 6);
		givenTheRetailersExist(RETAILER);
		givenTheCataloguesBelongTo(Map.of(CATALOGUE_GR, RETAILER, OTHER_RETAILERS_CATALOGUE, OTHER_RETAILER));

		var failure = applyAndExpectFailure(new ProductChangePage(5L, 6L, List.of(change(RETAILER, "yoghurt", 6, CATALOGUE_GR, OTHER_RETAILERS_CATALOGUE))));

		assertBadInput("catalogueId", "The Catalogue belongs to another Retailer, catalogueId: " + OTHER_RETAILERS_CATALOGUE.asString(), failure);
		verifyNoInteractions(mockRetailerProductDao);
	}

	@Test
	void aPageNamesItsSequencesAndCarriesItsChanges() {
		assertMissing("fromSequence", assertThrows(RequiredInputMissingException.class, () -> sut.apply(CATALOGUE_SYNC_USER, new ProductChangePage(null, 1L, List.of()))));
		assertMissing("toSequence", assertThrows(RequiredInputMissingException.class, () -> sut.apply(CATALOGUE_SYNC_USER, new ProductChangePage(0L, null, List.of()))));
		assertMissing("products", assertThrows(RequiredInputMissingException.class, () -> sut.apply(CATALOGUE_SYNC_USER, new ProductChangePage(0L, 1L, null))));
		assertNothingTouched();
	}

	@Test
	void aPageEndingBeforeItStartsIsRefused() {
		var e = assertThrows(BadInputValueException.class, () -> sut.apply(CATALOGUE_SYNC_USER, new ProductChangePage(5L, 4L, List.of())));

		assertBadInput("toSequence", "It must not be before fromSequence", e);
		assertNothingTouched();
	}

	/**
	 * A page of no changes still has to start at the cursor, which it leaves where it is.
	 */
	@Test
	void anEmptyPageIsCheckedAgainstTheCursor() {
		givenTheCursorAdvances(5, 5);
		givenTheCataloguesBelongTo(Map.of());
		when(mockRetailerProductDao.apply(any(), eq(List.of()))).thenReturn(Uni.createFrom().voidItem());

		sut.apply(CATALOGUE_SYNC_USER, new ProductChangePage(5L, 5L, List.of())).await().atMost(WAIT);

		verify(mockCursorDao).advance(any(), eq(5L), eq(5L));
		verify(mockRetailerDao, never()).findActiveFlag(any(), any());
	}

	@Test
	void aChangeAtOrBeforeTheStartOfThePageIsRefused() {
		var e = assertThrows(BadInputValueException.class, () -> sut.apply(CATALOGUE_SYNC_USER, new ProductChangePage(5L, 9L, List.of(
				change(RETAILER, "yoghurt", 6, CATALOGUE_GR),
				change(RETAILER, "feta", 5, CATALOGUE_GR)
		))));

		assertBadInput("products", "A change is outside the sequences of the page, changeSequence: 5", e);
		assertNothingTouched();
	}

	@Test
	void aChangeAfterTheEndOfThePageIsRefused() {
		var e = assertThrows(BadInputValueException.class, () -> sut.apply(CATALOGUE_SYNC_USER, new ProductChangePage(5L, 9L, List.of(
				change(RETAILER, "yoghurt", 9, CATALOGUE_GR),
				change(RETAILER, "feta", 10, CATALOGUE_GR)
		))));

		assertBadInput("products", "A change is outside the sequences of the page, changeSequence: 10", e);
		assertNothingTouched();
	}

	private void givenTheCursorAdvances(long fromSequence, long toSequence) {
		when(mockCursorDao.advance(any(), eq(fromSequence), eq(toSequence))).thenReturn(Uni.createFrom().item(true));
	}

	private void givenTheRetailersExist(RetailerId... retailerIds) {
		for (var retailerId : retailerIds) {
			when(mockRetailerDao.findActiveFlag(any(), eq(retailerId))).thenReturn(Uni.createFrom().item(true));
		}
	}

	private void givenTheCataloguesBelongTo(Map<CatalogueId, RetailerId> retailersOfCatalogues) {
		when(mockCatalogueDao.findRetailers(any(), any())).thenReturn(Uni.createFrom().item(retailersOfCatalogues));
	}

	private Throwable applyAndExpectFailure(ProductChangePage page) {
		return sut.apply(CATALOGUE_SYNC_USER, page)
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitFailure(WAIT)
				.getFailure();
	}

	private static void assertMissing(String inputName, RequiredInputMissingException failure) {
		assertEquals(inputName, failure.getInputName());
	}

	private void assertNothingTouched() {
		assertTrue(mockReactivePersistenceContextFactory.getOpenedTransactions().isEmpty(), "no transaction opened");
		verifyNoInteractions(mockCursorDao, mockRetailerDao, mockCatalogueDao, mockRetailerProductDao);
	}

	private static void assertBadInput(String inputName, String hint, Throwable failure) {
		var e = assertInstanceOf(BadInputValueException.class, failure);
		assertEquals(inputName, e.getInputName());
		assertEquals(hint, e.getHint());
	}

	private static ProductChange change(RetailerId retailerId, String productKey, long changeSequence, CatalogueId... catalogueIds) {
		var change = ImmutableProductChange.builder()
				.retailerId(retailerId)
				.productKey(new ProductKeyImpl(productKey))
				.changeSequence(changeSequence)
				.isRemoved(false)
				.inputHash("h" + changeSequence)
				.names(Map.of("el", productKey));
		for (var catalogueId : catalogueIds) {
			change.addListings(ImmutableListing.builder()
					.catalogueId(catalogueId)
					.price(new BigDecimal("1.99"))
					.currency(Currency.getInstance("EUR"))
					.isActive(true)
					.build());
		}
		return change.build();
	}
}
