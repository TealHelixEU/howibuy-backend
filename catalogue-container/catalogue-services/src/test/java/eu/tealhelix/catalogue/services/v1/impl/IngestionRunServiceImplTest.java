package eu.tealhelix.catalogue.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.dao.CatalogueDao;
import eu.tealhelix.catalogue.dao.IngestionRunDao;
import eu.tealhelix.catalogue.v1.model.ImmutableCatalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionReject;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRun;
import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import eu.tealhelix.common.services.authz.impl.TealHelixAuthorizationImpl;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.authorization.NotAuthenticatedException;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.v1.model.Role;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.smallrye.mutiny.Uni;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Who may read which Ingestion Run and its rejects, and which pages of rejects are refused, with the database mocked.
 */
@EnableAutoWeld
@AddBeanClasses(TealHelixAuthorizationImpl.class)
@ExtendWith(MockitoExtension.class)
public class IngestionRunServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final String RETAILER = "6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11";
	private static final User RETAILER_USER = new UserImpl(new UserIdImpl(RETAILER), null, null, false, false, Set.of(Role.RETAILER));
	private static final User OTHER_RETAILER_USER = new UserImpl(new UserIdImpl("0b6a2f4e-35c4-4d8e-a1a7-5e2c9f8d7b22"), null, null, false, false, Set.of(Role.RETAILER));
	private static final User OPERATOR_USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, true, Set.of(Role.OPERATOR));
	private static final User CATALOGUE_SYNC_USER = new UserImpl(new UserIdImpl("518cae6a-f2b2-4454-b74d-f2404feab2f5"), null, null, false, false, Set.of(Role.CATALOGUE_SYNC));
	private static final User PERSON_WITHOUT_ROLES = new UserImpl(new UserIdImpl(RETAILER), null, null, false, true, Set.of());

	private static final CatalogueId CATALOGUE = new CatalogueIdImpl("test-gr");
	private static final IngestionRunId RUN_ID = new IngestionRunIdImpl("5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e01");
	private static final IngestionRun RUN = ImmutableIngestionRun.builder()
			.id(RUN_ID)
			.catalogueId(CATALOGUE)
			.feedType(FeedType.SNAPSHOT)
			.status(IngestionRunStatus.RECEIVED)
			.receivedAt(LocalDateTime.of(2026, 10, 20, 12, 30, 15))
			.filePath("test-gr/5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e01.gz")
			.fileSize(17)
			.fileSha256("372c3f4cc881b55653a9c026ce87f1455636e0a1fbea9f1af2b3435442a85bd1")
			.build();
	private static final List<IngestionReject> REJECTS = List.of(ImmutableIngestionReject.builder().position(2).reason("No price").build());

	@Produces
	@Mock
	CatalogueDao mockCatalogueDao;

	@Produces
	@Mock
	IngestionRunDao mockIngestionRunDao;

	@Produces
	@RegisterExtension
	private MockReactivePersistenceContextFactory mockReactivePersistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Inject
	IngestionRunServiceImpl sut;

	@Test
	void theRetailerOfTheCatalogueReadsTheRun() {
		givenTheRunOfTheRetailer();

		assertEquals(RUN, sut.find(RETAILER_USER, RUN_ID).await().atMost(WAIT));
	}

	@Test
	void anOperatorReadsTheRunOfAnyRetailer() {
		when(mockIngestionRunDao.find(any(), eq(RUN_ID))).thenReturn(Uni.createFrom().item(RUN));

		assertEquals(RUN, sut.find(OPERATOR_USER, RUN_ID).await().atMost(WAIT));
	}

	@Test
	void nobodyElseReadsTheRun() {
		givenTheRunOfTheRetailer();

		assertThrows(NotAuthorizedException.class, () -> sut.find(OTHER_RETAILER_USER, RUN_ID).await().atMost(WAIT));
		assertThrows(NotAuthorizedException.class, () -> sut.find(CATALOGUE_SYNC_USER, RUN_ID).await().atMost(WAIT));
		assertThrows(NotAuthorizedException.class, () -> sut.find(PERSON_WITHOUT_ROLES, RUN_ID).await().atMost(WAIT));
	}

	@Test
	void anUnauthenticatedCallerReadsNothing() {
		assertThrows(NotAuthenticatedException.class, () -> sut.find(null, RUN_ID).await().atMost(WAIT));
		assertThrows(NotAuthenticatedException.class, () -> sut.findRejects(null, RUN_ID, 0, 10).await().atMost(WAIT));
		verifyNoInteractions(mockIngestionRunDao, mockCatalogueDao);
	}

	@Test
	void anUnknownRunIsNotFound() {
		when(mockIngestionRunDao.find(any(), eq(RUN_ID))).thenReturn(Uni.createFrom().nullItem());

		assertThrows(NotFoundException.class, () -> sut.find(OPERATOR_USER, RUN_ID).await().atMost(WAIT));
		assertThrows(NotFoundException.class, () -> sut.findRejects(OPERATOR_USER, RUN_ID, 0, 10).await().atMost(WAIT));
	}

	@Test
	void theRetailerOfTheCatalogueReadsAPageOfTheRejects() {
		givenTheRunOfTheRetailer();
		when(mockIngestionRunDao.findRejects(any(), eq(RUN_ID), eq(20), eq(10))).thenReturn(Uni.createFrom().item(REJECTS));

		assertEquals(REJECTS, sut.findRejects(RETAILER_USER, RUN_ID, 20, 10).await().atMost(WAIT));
	}

	@Test
	void anOperatorReadsAPageOfTheRejectsOfAnyRetailer() {
		when(mockIngestionRunDao.find(any(), eq(RUN_ID))).thenReturn(Uni.createFrom().item(RUN));
		when(mockIngestionRunDao.findRejects(any(), eq(RUN_ID), eq(0), eq(1000))).thenReturn(Uni.createFrom().item(REJECTS));

		assertEquals(REJECTS, sut.findRejects(OPERATOR_USER, RUN_ID, 0, 1000).await().atMost(WAIT));
	}

	@Test
	void nobodyElseReadsTheRejects() {
		givenTheRunOfTheRetailer();

		assertThrows(NotAuthorizedException.class, () -> sut.findRejects(OTHER_RETAILER_USER, RUN_ID, 0, 10).await().atMost(WAIT));
		verify(mockIngestionRunDao, never()).findRejects(any(), any(), anyInt(), anyInt());
	}

	@Test
	void aPageOutOfRangeIsRefused() {
		assertThrows(BadInputValueException.class, () -> sut.findRejects(OPERATOR_USER, RUN_ID, -1, 10).await().atMost(WAIT));
		assertThrows(BadInputValueException.class, () -> sut.findRejects(OPERATOR_USER, RUN_ID, 0, 0).await().atMost(WAIT));
		assertThrows(BadInputValueException.class, () -> sut.findRejects(OPERATOR_USER, RUN_ID, 0, 1001).await().atMost(WAIT));
		verifyNoInteractions(mockIngestionRunDao);
	}

	@Test
	void runsAreReadOutsideATransaction() {
		givenTheRunOfTheRetailer();

		sut.find(RETAILER_USER, RUN_ID).await().atMost(WAIT);

		assertEquals(List.of(), mockReactivePersistenceContextFactory.getOpenedTransactions());
	}

	private void givenTheRunOfTheRetailer() {
		when(mockIngestionRunDao.find(any(), eq(RUN_ID))).thenReturn(Uni.createFrom().item(RUN));
		when(mockCatalogueDao.find(any(), eq(CATALOGUE))).thenReturn(Uni.createFrom().item(ImmutableCatalogue.builder()
				.id(CATALOGUE)
				.retailerId(new GenericRetailerId(RETAILER))
				.country("GR")
				.primaryLanguage("el")
				.acceptedLanguages(Set.of("el"))
				.adapter("test-adapter")
				.isActive(true)
				.build()));
	}
}
