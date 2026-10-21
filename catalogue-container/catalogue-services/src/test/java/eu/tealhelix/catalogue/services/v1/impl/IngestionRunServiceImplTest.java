package eu.tealhelix.catalogue.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
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
import eu.tealhelix.catalogue.dao.RetailerProductDao;
import eu.tealhelix.catalogue.services.v1.types.IngestionRunNotHeldException;
import eu.tealhelix.catalogue.services.v1.types.IngestionRunNotReplayableException;
import eu.tealhelix.catalogue.v1.model.ImmutableCatalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionReject;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRun;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRunCounts;
import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import eu.tealhelix.common.services.authz.impl.TealHelixAuthorizationImpl;
import eu.tealhelix.common.services.generic.DateTimeService;
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
import org.jboss.weld.junit5.auto.ExcludeBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Who may read which Ingestion Run and its rejects, and which pages of rejects are refused; the decisions on held runs;
 * which runs are replayed and how. The database and the feed store are mocked.
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
	private static final IngestionRun HELD_RUN = ImmutableIngestionRun.builder()
			.from(RUN)
			.status(IngestionRunStatus.HELD)
			.adapter("test-adapter")
			.adapterVersion("1")
			.startedAt(LocalDateTime.of(2026, 10, 20, 12, 30, 16))
			.counts(ImmutableIngestionRunCounts.builder().total(4).valid(4).rejected(0).added(0).changed(1).unchanged(3).missing(3).deactivated(0).build())
			.build();
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 15, 0, 0);
	private static final List<IngestionReject> REJECTS = List.of(ImmutableIngestionReject.builder().position(2).reason("No price").build());

	@Produces
	@Mock
	CatalogueDao mockCatalogueDao;

	@Produces
	@Mock
	IngestionRunDao mockIngestionRunDao;

	@Produces
	@Mock
	RetailerProductDao mockRetailerProductDao;

	@Produces
	@Mock
	DateTimeService mockDateTimeService;

	@Produces
	@ExcludeBean
	@Mock
	IngestionRunDispatcher mockDispatcher;

	@Produces
	@Mock
	FeedStore mockFeedStore;

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

	@Test
	void anOperatorConfirmsAHeldRunWhichTakesTheMissingListingsOffSale() {
		givenTheHeldRun();
		when(mockRetailerProductDao.deactivateUnseenListings(any(), eq(CATALOGUE), eq(RUN_ID))).thenReturn(Uni.createFrom().item(3));
		var done = ImmutableIngestionRun.builder()
				.from(HELD_RUN)
				.status(IngestionRunStatus.DONE)
				.finishedAt(NOW)
				.counts(ImmutableIngestionRunCounts.copyOf(HELD_RUN.getCounts()).withDeactivated(3))
				.build();
		when(mockIngestionRunDao.updateHeld(any(), eq(done))).thenReturn(Uni.createFrom().item(true));

		assertEquals(done, sut.confirm(OPERATOR_USER, RUN_ID).await().atMost(WAIT));

		var transactions = mockReactivePersistenceContextFactory.getOpenedTransactions();
		assertEquals(1, transactions.size());
		verify(mockRetailerProductDao).deactivateUnseenListings(transactions.getFirst(), CATALOGUE, RUN_ID);
		verify(mockIngestionRunDao).updateHeld(transactions.getFirst(), done);
		verify(mockDispatcher).wake(CATALOGUE);
	}

	@Test
	void anOperatorDiscardsAHeldRunWhichTakesNothingOffSale() {
		givenTheHeldRun();
		var discarded = ImmutableIngestionRun.builder().from(HELD_RUN).status(IngestionRunStatus.DISCARDED).finishedAt(NOW).build();
		when(mockIngestionRunDao.updateHeld(any(), eq(discarded))).thenReturn(Uni.createFrom().item(true));

		assertEquals(discarded, sut.discard(OPERATOR_USER, RUN_ID).await().atMost(WAIT));

		verifyNoInteractions(mockRetailerProductDao);
		verify(mockDispatcher).wake(CATALOGUE);
	}

	@Test
	void onlyAnOperatorDecidesOnAHeldRun() {
		for (var caller : List.of(RETAILER_USER, CATALOGUE_SYNC_USER, PERSON_WITHOUT_ROLES)) {
			assertThrows(NotAuthorizedException.class, () -> sut.confirm(caller, RUN_ID), caller.getRoles().toString());
			assertThrows(NotAuthorizedException.class, () -> sut.discard(caller, RUN_ID), caller.getRoles().toString());
		}
		assertThrows(NotAuthenticatedException.class, () -> sut.confirm(null, RUN_ID));
		assertThrows(NotAuthenticatedException.class, () -> sut.discard(null, RUN_ID));
		verifyNoInteractions(mockIngestionRunDao, mockRetailerProductDao, mockDispatcher);
	}

	@Test
	void aRunThatIsNotHeldIsNeitherConfirmedNorDiscarded() {
		when(mockIngestionRunDao.find(any(), eq(RUN_ID))).thenReturn(Uni.createFrom().item(RUN));

		var confirmation = assertThrows(IngestionRunNotHeldException.class, () -> sut.confirm(OPERATOR_USER, RUN_ID).await().atMost(WAIT));
		assertThrows(IngestionRunNotHeldException.class, () -> sut.discard(OPERATOR_USER, RUN_ID).await().atMost(WAIT));

		assertEquals("The Ingestion Run is not held, status: RECEIVED, runId: " + RUN_ID.asString(), confirmation.getMessage());
		verifyNoInteractions(mockRetailerProductDao, mockDispatcher);
		verify(mockIngestionRunDao, never()).updateHeld(any(), any());
	}

	@Test
	void aRunThatAnotherOperatorDecidedOnMeanwhileIsAConflict() {
		givenTheHeldRun();
		when(mockRetailerProductDao.deactivateUnseenListings(any(), eq(CATALOGUE), eq(RUN_ID))).thenReturn(Uni.createFrom().item(3));
		when(mockIngestionRunDao.updateHeld(any(), any())).thenReturn(Uni.createFrom().item(false));

		assertThrows(IngestionRunNotHeldException.class, () -> sut.confirm(OPERATOR_USER, RUN_ID).await().atMost(WAIT));
		assertThrows(IngestionRunNotHeldException.class, () -> sut.discard(OPERATOR_USER, RUN_ID).await().atMost(WAIT));

		verifyNoInteractions(mockDispatcher);
	}

	@Test
	void anUnknownRunIsNeitherConfirmedNorDiscarded() {
		when(mockIngestionRunDao.find(any(), eq(RUN_ID))).thenReturn(Uni.createFrom().nullItem());

		assertThrows(NotFoundException.class, () -> sut.confirm(OPERATOR_USER, RUN_ID).await().atMost(WAIT));
		assertThrows(NotFoundException.class, () -> sut.discard(OPERATOR_USER, RUN_ID).await().atMost(WAIT));
	}

	@Test
	void anOperatorReplaysAFinishedRunOnItsStoredFeed() {
		for (var status : List.of(IngestionRunStatus.DONE, IngestionRunStatus.FAILED, IngestionRunStatus.DISCARDED)) {
			var finished = finished(FeedType.DELTA, status);
			when(mockIngestionRunDao.find(any(), eq(RUN_ID))).thenReturn(Uni.createFrom().item(finished));
			when(mockFeedStore.exists(RUN.getFilePath())).thenReturn(true);
			when(mockIngestionRunDao.create(any(), any())).thenReturn(Uni.createFrom().voidItem());

			var replay = sut.replay(OPERATOR_USER, RUN_ID).await().atMost(WAIT);

			assertNotEquals(RUN_ID, replay.getId(), status.name());
			assertEquals(ImmutableIngestionRun.builder()
					.id(replay.getId())
					.catalogueId(CATALOGUE)
					.feedType(FeedType.DELTA)
					.status(IngestionRunStatus.RECEIVED)
					.receivedAt(RUN.getReceivedAt())
					.filePath(RUN.getFilePath())
					.fileSize(RUN.getFileSize())
					.fileSha256(RUN.getFileSha256())
					.replayOfRunId(RUN_ID)
					.build(), replay, status.name());
			verify(mockIngestionRunDao).create(mockReactivePersistenceContextFactory.getOpenedTransactions().getLast(), replay);
			verify(mockIngestionRunDao, never()).update(any(), any());
			verify(mockDispatcher).wake(CATALOGUE);
			reset(mockIngestionRunDao, mockDispatcher);
		}
	}

	@Test
	void aSnapshotIsReplayedWhileNoLaterSnapshotSupersedesIt() {
		when(mockIngestionRunDao.find(any(), eq(RUN_ID))).thenReturn(Uni.createFrom().item(finished(FeedType.SNAPSHOT, IngestionRunStatus.DONE)));
		when(mockIngestionRunDao.hasSnapshotReceivedAfter(any(), eq(CATALOGUE), eq(RUN.getReceivedAt()))).thenReturn(Uni.createFrom().item(false));
		when(mockFeedStore.exists(RUN.getFilePath())).thenReturn(true);
		when(mockIngestionRunDao.create(any(), any())).thenReturn(Uni.createFrom().voidItem());

		assertEquals(RUN_ID, sut.replay(OPERATOR_USER, RUN_ID).await().atMost(WAIT).getReplayOfRunId());
	}

	@Test
	void aSnapshotThatALaterSnapshotSupersedesIsNotReplayed() {
		when(mockIngestionRunDao.find(any(), eq(RUN_ID))).thenReturn(Uni.createFrom().item(finished(FeedType.SNAPSHOT, IngestionRunStatus.DONE)));
		when(mockIngestionRunDao.hasSnapshotReceivedAfter(any(), eq(CATALOGUE), eq(RUN.getReceivedAt()))).thenReturn(Uni.createFrom().item(true));

		var e = assertThrows(IngestionRunNotReplayableException.class, () -> sut.replay(OPERATOR_USER, RUN_ID).await().atMost(WAIT));

		assertEquals("A later Snapshot of the Catalogue supersedes the Ingestion Run, runId: " + RUN_ID.asString(), e.getMessage());
		verify(mockIngestionRunDao, never()).create(any(), any());
		verifyNoInteractions(mockDispatcher);
	}

	@Test
	void aRunThatHasNotFinishedIsNotReplayed() {
		for (var status : List.of(IngestionRunStatus.RECEIVED, IngestionRunStatus.PROCESSING, IngestionRunStatus.HELD)) {
			when(mockIngestionRunDao.find(any(), eq(RUN_ID))).thenReturn(Uni.createFrom().item(ImmutableIngestionRun.copyOf(RUN).withStatus(status)));

			var e = assertThrows(IngestionRunNotReplayableException.class, () -> sut.replay(OPERATOR_USER, RUN_ID).await().atMost(WAIT));

			assertEquals("Only a finished Ingestion Run is replayed, status: " + status + ", runId: " + RUN_ID.asString(), e.getMessage());
		}
		verify(mockIngestionRunDao, never()).create(any(), any());
		verifyNoInteractions(mockDispatcher);
	}

	@Test
	void aRunWhoseFeedIsNoLongerStoredIsNotReplayed() {
		when(mockIngestionRunDao.find(any(), eq(RUN_ID))).thenReturn(Uni.createFrom().item(finished(FeedType.DELTA, IngestionRunStatus.DONE)));
		when(mockFeedStore.exists(RUN.getFilePath())).thenReturn(false);

		var e = assertThrows(NotFoundException.class, () -> sut.replay(OPERATOR_USER, RUN_ID).await().atMost(WAIT));

		assertEquals("The Feed of the Ingestion Run is no longer stored, runId: " + RUN_ID.asString(), e.getMessage());
		verify(mockIngestionRunDao, never()).create(any(), any());
		verifyNoInteractions(mockDispatcher);
	}

	@Test
	void anUnknownRunIsNotReplayed() {
		when(mockIngestionRunDao.find(any(), eq(RUN_ID))).thenReturn(Uni.createFrom().nullItem());

		assertThrows(NotFoundException.class, () -> sut.replay(OPERATOR_USER, RUN_ID).await().atMost(WAIT));
		verifyNoInteractions(mockDispatcher);
	}

	@Test
	void onlyAnOperatorReplaysARun() {
		for (var caller : List.of(RETAILER_USER, CATALOGUE_SYNC_USER, PERSON_WITHOUT_ROLES)) {
			assertThrows(NotAuthorizedException.class, () -> sut.replay(caller, RUN_ID), caller.getRoles().toString());
		}
		assertThrows(NotAuthenticatedException.class, () -> sut.replay(null, RUN_ID));
		verifyNoInteractions(mockIngestionRunDao, mockFeedStore, mockDispatcher);
	}

	/**
	 * The run, processed to its end with the adapter of the time.
	 */
	private static IngestionRun finished(FeedType feedType, IngestionRunStatus status) {
		return ImmutableIngestionRun.builder()
				.from(RUN)
				.feedType(feedType)
				.status(status)
				.adapter("test-adapter")
				.adapterVersion("1")
				.startedAt(LocalDateTime.of(2026, 10, 20, 12, 30, 16))
				.finishedAt(LocalDateTime.of(2026, 10, 20, 12, 31, 0))
				.error(status == IngestionRunStatus.FAILED ? "The Feed cannot be read" : null)
				.build();
	}

	private void givenTheHeldRun() {
		when(mockIngestionRunDao.find(any(), eq(RUN_ID))).thenReturn(Uni.createFrom().item(HELD_RUN));
		when(mockDateTimeService.getNow()).thenReturn(NOW);
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
