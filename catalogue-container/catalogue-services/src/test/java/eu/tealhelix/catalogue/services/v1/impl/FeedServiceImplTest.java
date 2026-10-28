package eu.tealhelix.catalogue.services.v1.impl;

import static eu.tealhelix.catalogue.v1.types.FeedIdTestUtils.matchesHasFeedId;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.dao.CatalogueDao;
import eu.tealhelix.catalogue.dao.FeedDao;
import eu.tealhelix.catalogue.dao.IngestionRunDao;
import eu.tealhelix.catalogue.services.v1.types.FeedWithRuns;
import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.model.Feed;
import eu.tealhelix.catalogue.v1.model.ImmutableCatalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableFeed;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRun;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.FeedId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.FeedIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
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
import org.jboss.weld.junit5.auto.ExcludeBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Who may send a Feed for which Catalogues and read it back, and how the stored Feed and its runs are kept together,
 * with the database and the feed store mocked. What the store does with the body is covered by {@code FeedStoreImplTest}.
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
	private static final CatalogueId OTHER_CATALOGUE = new CatalogueIdImpl("test-cy");
	private static final FeedId FEED_ID = new FeedIdImpl("3a0c5e7d-9b1f-4d2e-8c6a-5f4b3e2d1c0a");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 30, 15);
	private static final StoredFeed STORED_FEED = new StoredFeed(RETAILER + "/stored.gz", 17, "372c3f4cc881b55653a9c026ce87f1455636e0a1fbea9f1af2b3435442a85bd1");

	@Produces
	@Mock
	CatalogueDao mockCatalogueDao;

	@Produces
	@Mock
	FeedDao mockFeedDao;

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
	@ExcludeBean
	@Mock
	IngestionRunDispatcher mockDispatcher;

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
		givenTheCatalogue(catalogue(CATALOGUE, RETAILER, true));
		var feedId = givenTheFeedIsStoredAndRecorded();

		var received = sut.receive(RETAILER_USER, CATALOGUE, FeedType.DELTA, body);

		var feed = receivedFeed(feedId.getValue(), FeedType.DELTA);
		assertEquals(feed, received.feed());
		assertEquals(1, received.runs().size());
		var run = received.runs().getFirst();
		assertEquals(receivedRun(run, feed, CATALOGUE), run);
		thenTheyAreRecordedTogetherAndProcessed(received);
	}

	@Test
	void aRetailerSendsOneFeedForAllItsActiveCatalogues() {
		when(mockCatalogueDao.findActive(any(), eq(new GenericRetailerId(RETAILER))))
				.thenReturn(Uni.createFrom().item(List.of(catalogue(OTHER_CATALOGUE, RETAILER, true), catalogue(CATALOGUE, RETAILER, true))));
		var feedId = givenTheFeedIsStoredAndRecorded();

		var received = sut.receiveForActiveCatalogues(RETAILER_USER, FeedType.SNAPSHOT, body);

		var feed = receivedFeed(feedId.getValue(), FeedType.SNAPSHOT);
		assertEquals(feed, received.feed());
		assertEquals(2, received.runs().size());
		var first = received.runs().get(0);
		var second = received.runs().get(1);
		assertEquals(receivedRun(first, feed, OTHER_CATALOGUE), first);
		assertEquals(receivedRun(second, feed, CATALOGUE), second);
		thenTheyAreRecordedTogetherAndProcessed(received);
	}

	@Test
	void aRetailerWithoutActiveCataloguesSendsNoFeed() {
		when(mockCatalogueDao.findActive(any(), eq(new GenericRetailerId(RETAILER)))).thenReturn(Uni.createFrom().item(List.of()));

		assertThrows(NotAuthorizedException.class, () -> sut.receiveForActiveCatalogues(RETAILER_USER, FeedType.SNAPSHOT, body));
		verifyNoInteractions(mockFeedStore, mockFeedDao, mockIngestionRunDao, mockDispatcher);
	}

	@Test
	void onlyRetailersSendFeeds() {
		assertThrows(NotAuthenticatedException.class, () -> sut.receive(null, CATALOGUE, FeedType.SNAPSHOT, body));
		assertThrows(NotAuthorizedException.class, () -> sut.receive(OPERATOR_USER, CATALOGUE, FeedType.SNAPSHOT, body));
		assertThrows(NotAuthorizedException.class, () -> sut.receive(CATALOGUE_SYNC_USER, CATALOGUE, FeedType.SNAPSHOT, body));
		assertThrows(NotAuthenticatedException.class, () -> sut.receiveForActiveCatalogues(null, FeedType.SNAPSHOT, body));
		assertThrows(NotAuthorizedException.class, () -> sut.receiveForActiveCatalogues(OPERATOR_USER, FeedType.SNAPSHOT, body));
		assertThrows(NotAuthorizedException.class, () -> sut.receiveForActiveCatalogues(CATALOGUE_SYNC_USER, FeedType.SNAPSHOT, body));
		verifyNoInteractions(mockCatalogueDao, mockFeedStore, mockFeedDao, mockIngestionRunDao, mockDispatcher);
	}

	@Test
	void aFeedForAnUnknownCatalogueIsNotRead() {
		givenTheCatalogue(null);

		assertThrows(NotFoundException.class, () -> sut.receive(RETAILER_USER, CATALOGUE, FeedType.SNAPSHOT, body));
		verifyNoInteractions(mockFeedStore, mockFeedDao, mockIngestionRunDao, mockDispatcher);
	}

	@Test
	void aFeedForAnotherRetailersCatalogueIsNotRead() {
		givenTheCatalogue(catalogue(CATALOGUE, RETAILER, true));

		assertThrows(NotAuthorizedException.class, () -> sut.receive(OTHER_RETAILER_USER, CATALOGUE, FeedType.SNAPSHOT, body));
		verifyNoInteractions(mockFeedStore, mockFeedDao, mockIngestionRunDao, mockDispatcher);
	}

	@Test
	void aFeedForAnInactiveCatalogueIsNotRead() {
		givenTheCatalogue(catalogue(CATALOGUE, RETAILER, false));

		assertThrows(NotAuthorizedException.class, () -> sut.receive(RETAILER_USER, CATALOGUE, FeedType.SNAPSHOT, body));
		verifyNoInteractions(mockFeedStore, mockFeedDao, mockIngestionRunDao, mockDispatcher);
	}

	@Test
	void theStoredFeedIsRemovedWhenItsRunsCannotBeRecorded() {
		givenTheCatalogue(catalogue(CATALOGUE, RETAILER, true));
		when(mockFeedStore.store(eq(new GenericRetailerId(RETAILER)), any(), eq(body))).thenReturn(STORED_FEED);
		when(mockDateTimeService.getNow()).thenReturn(NOW);
		when(mockFeedDao.create(any(), any())).thenReturn(Uni.createFrom().voidItem());
		var failure = new IllegalStateException("The database is down");
		when(mockIngestionRunDao.create(any(), any())).thenReturn(Uni.createFrom().failure(failure));

		var thrown = assertThrows(IllegalStateException.class, () -> sut.receive(RETAILER_USER, CATALOGUE, FeedType.SNAPSHOT, body));

		assertSame(failure, thrown);
		verify(mockFeedStore).delete(STORED_FEED.path());
		verifyNoInteractions(mockDispatcher);
	}

	@Test
	void anOperatorReadsAnyFeed() {
		var feed = givenTheFeedWithItsRun(OTHER_RETAILER);

		var read = sut.find(OPERATOR_USER, FEED_ID).await().atMost(Duration.ofSeconds(300));

		assertEquals(feed, read);
	}

	@Test
	void aRetailerReadsTheFeedsItSent() {
		var feed = givenTheFeedWithItsRun(RETAILER);

		var read = sut.find(RETAILER_USER, FEED_ID).await().atMost(Duration.ofSeconds(300));

		assertEquals(feed, read);
	}

	@Test
	void aRetailerDoesNotReadTheFeedsOfAnother() {
		givenTheFeed(feed(OTHER_RETAILER));

		assertThrows(NotAuthorizedException.class, () -> sut.find(RETAILER_USER, FEED_ID).await().atMost(Duration.ofSeconds(300)));
	}

	@Test
	void onlyRetailersAndOperatorsReadFeeds() {
		assertThrows(NotAuthenticatedException.class, () -> sut.find(null, FEED_ID));
		givenTheFeed(feed(RETAILER));

		assertThrows(NotAuthorizedException.class, () -> sut.find(CATALOGUE_SYNC_USER, FEED_ID).await().atMost(Duration.ofSeconds(300)));
	}

	@Test
	void anUnknownFeedIsNotFound() {
		givenTheFeed(null);

		assertThrows(NotFoundException.class, () -> sut.find(OPERATOR_USER, FEED_ID).await().atMost(Duration.ofSeconds(300)));
	}

	private void givenTheCatalogue(Catalogue catalogue) {
		when(mockCatalogueDao.find(any(), eq(CATALOGUE))).thenReturn(Uni.createFrom().item(catalogue));
	}

	/**
	 * @return Captures the id of the Feed
	 */
	private ArgumentCaptor<FeedId> givenTheFeedIsStoredAndRecorded() {
		var feedId = ArgumentCaptor.forClass(FeedId.class);
		when(mockFeedStore.store(eq(new GenericRetailerId(RETAILER)), feedId.capture(), eq(body))).thenReturn(STORED_FEED);
		when(mockDateTimeService.getNow()).thenReturn(NOW);
		when(mockFeedDao.create(any(), any())).thenReturn(Uni.createFrom().voidItem());
		when(mockIngestionRunDao.create(any(), any())).thenReturn(Uni.createFrom().voidItem());
		return feedId;
	}

	private void thenTheyAreRecordedTogetherAndProcessed(FeedWithRuns received) {
		var transaction = mockReactivePersistenceContextFactory.getOpenedTransactions().getFirst();
		verify(mockFeedDao).create(transaction, received.feed());
		verify(mockIngestionRunDao).create(transaction, received.runs());
		received.runs().forEach(run -> verify(mockDispatcher).wake(run.getCatalogueId()));
	}

	private static Feed receivedFeed(FeedId feedId, FeedType feedType) {
		return ImmutableFeed.builder()
				.id(feedId)
				.retailerId(new GenericRetailerId(RETAILER))
				.feedType(feedType)
				.receivedAt(NOW)
				.filePath(STORED_FEED.path())
				.fileSize(STORED_FEED.size())
				.fileSha256(STORED_FEED.sha256())
				.build();
	}

	/**
	 * A run is told apart by its id alone, which the service chooses; the adapter is chosen when processing starts.
	 */
	private static IngestionRun receivedRun(IngestionRun run, Feed feed, CatalogueId catalogueId) {
		return ImmutableIngestionRun.builder()
				.id(run.getId())
				.feed(feed)
				.catalogueId(catalogueId)
				.status(IngestionRunStatus.RECEIVED)
				.build();
	}

	private FeedWithRuns givenTheFeedWithItsRun(String retailer) {
		var feed = feed(retailer);
		givenTheFeed(feed);
		var runs = List.<IngestionRun>of(ImmutableIngestionRun.builder()
				.id(new IngestionRunIdImpl("7b2d4f6a-8c0e-4a1b-9d3f-5e7a9c1b3d5f"))
				.feed(feed)
				.catalogueId(CATALOGUE)
				.status(IngestionRunStatus.DONE)
				.build());
		when(mockIngestionRunDao.findOfFeed(any(), matchesHasFeedId(FEED_ID))).thenReturn(Uni.createFrom().item(runs));
		return new FeedWithRuns(feed, runs);
	}

	private void givenTheFeed(Feed feed) {
		when(mockFeedDao.find(any(), matchesHasFeedId(FEED_ID))).thenReturn(Uni.createFrom().item(feed));
	}

	private static Feed feed(String retailer) {
		return ImmutableFeed.builder()
				.id(FEED_ID)
				.retailerId(new GenericRetailerId(retailer))
				.feedType(FeedType.SNAPSHOT)
				.receivedAt(NOW)
				.filePath(retailer + "/" + FEED_ID.asString() + ".gz")
				.fileSize(17)
				.fileSha256("aa")
				.build();
	}

	private static Catalogue catalogue(CatalogueId catalogueId, String retailer, boolean active) {
		return ImmutableCatalogue.builder()
				.id(catalogueId)
				.retailerId(new GenericRetailerId(retailer))
				.country("GR")
				.primaryLanguage("el")
				.acceptedLanguages(Set.of("el"))
				.adapter("test-adapter")
				.isActive(active)
				.build();
	}
}
