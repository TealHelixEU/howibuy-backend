package eu.tealhelix.catalogue.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.Supplier;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.dao.CatalogueDao;
import eu.tealhelix.catalogue.dao.IngestionRunDao;
import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableCatalogue;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.services.authz.impl.TealHelixAuthorizationImpl;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.authorization.NotAuthenticatedException;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.entity.NotFoundException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Who may send a Feed for which Catalogue, and how the stored Feed and its run are kept together, with the database and
 * the feed store mocked. What the store does with the body is covered by {@code FeedStoreImplTest}.
 */
@EnableAutoWeld
@AddBeanClasses(TealHelixAuthorizationImpl.class)
@ExtendWith(MockitoExtension.class)
public class FeedServiceImplTest {
	private static final String RETAILER = "6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11";
	private static final String OTHER_RETAILER = "0b6a2f4e-35c4-4d8e-a1a7-5e2c9f8d7b22";
	private static final User RETAILER_USER = new UserImpl(new UserIdImpl(RETAILER), null, null, false, false, Set.of(Role.RETAILER));
	private static final User OTHER_RETAILER_USER = new UserImpl(new UserIdImpl(OTHER_RETAILER), null, null, false, false, Set.of(Role.RETAILER));
	private static final User OPERATOR_USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, true, Set.of(Role.OPERATOR));
	private static final User CATALOGUE_SYNC_USER = new UserImpl(new UserIdImpl("518cae6a-f2b2-4454-b74d-f2404feab2f5"), null, null, false, false, Set.of(Role.CATALOGUE_SYNC));

	private static final CatalogueId CATALOGUE = new CatalogueIdImpl("test-gr");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 30, 15);
	private static final StoredFeed STORED_FEED = new StoredFeed("test-gr/stored.gz", 17, "372c3f4cc881b55653a9c026ce87f1455636e0a1fbea9f1af2b3435442a85bd1");

	@Produces
	@Mock
	CatalogueDao mockCatalogueDao;

	@Produces
	@Mock
	IngestionRunDao mockIngestionRunDao;

	@Produces
	@Mock
	FeedStore mockFeedStore;

	@Produces
	@Mock
	DateTimeService mockDateTimeService;

	@Produces
	UniAwaiter uniAwaiter = new UniAwaiter() {
		@Override
		public <T> T await(Supplier<Uni<T>> call) {
			return call.get().await().atMost(Duration.ofSeconds(300));
		}
	};

	@Produces
	@RegisterExtension
	private MockReactivePersistenceContextFactory mockReactivePersistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Inject
	FeedServiceImpl sut;

	private final InputStream body = new ByteArrayInputStream("<feed>test</feed>".getBytes());

	@Test
	void aRetailerSendsAFeedForItsActiveCatalogue() {
		givenTheCatalogue(catalogue(RETAILER, true));
		var runId = ArgumentCaptor.forClass(IngestionRunId.class);
		when(mockFeedStore.store(eq(CATALOGUE), runId.capture(), eq(body))).thenReturn(STORED_FEED);
		when(mockDateTimeService.getNow()).thenReturn(NOW);
		when(mockIngestionRunDao.create(any(), any())).thenReturn(Uni.createFrom().voidItem());

		var run = sut.receive(RETAILER_USER, CATALOGUE, FeedType.DELTA, body);

		assertEquals(runId.getValue(), run.getId());
		assertEquals(CATALOGUE, run.getCatalogueId());
		assertEquals(FeedType.DELTA, run.getFeedType());
		assertEquals(IngestionRunStatus.RECEIVED, run.getStatus());
		assertEquals(NOW, run.getReceivedAt());
		assertEquals(STORED_FEED, new StoredFeed(run.getFilePath(), run.getFileSize(), run.getFileSha256()));
		assertNull(run.getAdapter(), "the adapter is chosen when processing starts");
		assertNull(run.getStartedAt());
		assertNull(run.getCounts());
		var transaction = mockReactivePersistenceContextFactory.getOpenedTransactions().getFirst();
		verify(mockIngestionRunDao).create(transaction, run);
	}

	@Test
	void onlyRetailersSendFeeds() {
		assertThrows(NotAuthenticatedException.class, () -> sut.receive(null, CATALOGUE, FeedType.SNAPSHOT, body));
		assertThrows(NotAuthorizedException.class, () -> sut.receive(OPERATOR_USER, CATALOGUE, FeedType.SNAPSHOT, body));
		assertThrows(NotAuthorizedException.class, () -> sut.receive(CATALOGUE_SYNC_USER, CATALOGUE, FeedType.SNAPSHOT, body));
		verifyNoInteractions(mockCatalogueDao, mockFeedStore, mockIngestionRunDao);
	}

	@Test
	void aFeedForAnUnknownCatalogueIsNotRead() {
		givenTheCatalogue(null);

		assertThrows(NotFoundException.class, () -> sut.receive(RETAILER_USER, CATALOGUE, FeedType.SNAPSHOT, body));
		verifyNoInteractions(mockFeedStore, mockIngestionRunDao);
	}

	@Test
	void aFeedForAnotherRetailersCatalogueIsNotRead() {
		givenTheCatalogue(catalogue(RETAILER, true));

		assertThrows(NotAuthorizedException.class, () -> sut.receive(OTHER_RETAILER_USER, CATALOGUE, FeedType.SNAPSHOT, body));
		verifyNoInteractions(mockFeedStore, mockIngestionRunDao);
	}

	@Test
	void aFeedForAnInactiveCatalogueIsNotRead() {
		givenTheCatalogue(catalogue(RETAILER, false));

		assertThrows(NotAuthorizedException.class, () -> sut.receive(RETAILER_USER, CATALOGUE, FeedType.SNAPSHOT, body));
		verifyNoInteractions(mockFeedStore, mockIngestionRunDao);
	}

	@Test
	void theStoredFeedIsRemovedWhenItsRunCannotBeRecorded() {
		givenTheCatalogue(catalogue(RETAILER, true));
		when(mockFeedStore.store(eq(CATALOGUE), any(), eq(body))).thenReturn(STORED_FEED);
		when(mockDateTimeService.getNow()).thenReturn(NOW);
		var failure = new IllegalStateException("The database is down");
		when(mockIngestionRunDao.create(any(), any())).thenReturn(Uni.createFrom().failure(failure));

		var thrown = assertThrows(IllegalStateException.class, () -> sut.receive(RETAILER_USER, CATALOGUE, FeedType.SNAPSHOT, body));

		assertSame(failure, thrown);
		verify(mockFeedStore).delete(STORED_FEED.path());
	}

	private void givenTheCatalogue(Catalogue catalogue) {
		when(mockCatalogueDao.find(any(), eq(CATALOGUE))).thenReturn(Uni.createFrom().item(catalogue));
	}

	private static Catalogue catalogue(String retailer, boolean active) {
		return ImmutableCatalogue.builder()
				.id(CATALOGUE)
				.retailerId(new GenericRetailerId(retailer))
				.country("GR")
				.primaryLanguage("el")
				.isActive(active)
				.build();
	}
}
