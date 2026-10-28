package eu.tealhelix.catalogue.services.v1.impl;

import static eu.tealhelix.catalogue.v1.types.CatalogueIdTestUtils.matchesHasCatalogueId;
import static eu.tealhelix.catalogue.v1.types.IngestionRunIdTestUtils.matchesHasIngestionRunId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import jakarta.enterprise.inject.Instance;

import eu.tealhelix.catalogue.dao.CatalogueDao;
import eu.tealhelix.catalogue.dao.IngestionRunDao;
import eu.tealhelix.catalogue.dao.RetailerProductDao;
import eu.tealhelix.catalogue.feeds.DeletionItem;
import eu.tealhelix.catalogue.feeds.DeletionRecord;
import eu.tealhelix.catalogue.feeds.FeedItem;
import eu.tealhelix.catalogue.feeds.FeedReader;
import eu.tealhelix.catalogue.feeds.FeedRecord;
import eu.tealhelix.catalogue.feeds.RejectedRecord;
import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableCatalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableFeed;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionReject;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRun;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRunCounts;
import eu.tealhelix.catalogue.v1.model.ImmutableListing;
import eu.tealhelix.catalogue.v1.model.ImmutableProductChange;
import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.model.Listing;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.FeedIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * How a run goes from the stored Feed to its counts, with the database and the feed store mocked: the batches, the
 * rejects, the stamps and the statuses. What the stored products look like is covered by {@code ProductRecordMergerTest},
 * and the database by the DB-behaviour tests of the deployable.
 */
@ExtendWith(MockitoExtension.class)
public class IngestionRunProcessorTest {
	private static final RetailerId RETAILER = new GenericRetailerId("6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11");
	private static final CatalogueId CATALOGUE = new CatalogueIdImpl("rimi-lt");
	private static final IngestionRunId RUN_ID = new IngestionRunIdImpl("5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e01");
	private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 10, 21, 9, 0, 0);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 10, 0, 0);
	private static final int BATCH_SIZE = 2;
	private static final double HOLD_SHARE = 0.1;
	private static final CatalogueId OTHER_CATALOGUE = new CatalogueIdImpl("rimi-lv");

	@Mock
	CatalogueDao catalogueDao;

	@Mock
	IngestionRunDao ingestionRunDao;

	@Mock
	RetailerProductDao retailerProductDao;

	@Mock
	FeedStore feedStore;

	@Mock
	DateTimeService dateTimeService;

	@Mock
	Instance<FeedReader> feedReaders;

	@RegisterExtension
	private MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	private final UniAwaiter uniAwaiter = new UniAwaiter() {
		@Override
		public <T> T await(Supplier<Uni<T>> call) {
			return call.get().await().atMost(Duration.ofSeconds(300));
		}
	};

	/** What the test reader reads; a {@code null} item stands for a Feed that breaks off there. */
	private final List<FeedItem> feed = new ArrayList<>();

	/** What the test reader reads from a Deletion Feed. */
	private final List<DeletionItem> deletions = new ArrayList<>();

	/** The products in the database, by key, as the processor stores them. */
	private final Map<ProductKey, ProductChange> database = new HashMap<>();

	private final List<Integer> sequencesTaken = new ArrayList<>();

	private final InputStream storedFeed = new ByteArrayInputStream(new byte[0]);

	private IngestionRunProcessor sut;

	@BeforeEach
	void setUp() {
		sut = new IngestionRunProcessor(catalogueDao, ingestionRunDao, retailerProductDao, feedStore, dateTimeService, uniAwaiter, persistenceContextFactory, feedReaders, BATCH_SIZE, HOLD_SHARE);
		lenient().when(feedReaders.stream()).thenAnswer(i -> Stream.of(new TestFeedReader()));
		lenient().when(catalogueDao.find(any(), matchesHasCatalogueId(CATALOGUE))).thenReturn(Uni.createFrom().item(catalogue("test-adapter")));
		lenient().when(dateTimeService.getNow()).thenReturn(NOW);
		lenient().when(feedStore.open("rimi-lt/run.gz")).thenReturn(storedFeed);
		lenient().when(ingestionRunDao.update(any(), any())).thenReturn(Uni.createFrom().voidItem());
		lenient().when(ingestionRunDao.deleteRejects(any(), any())).thenReturn(Uni.createFrom().voidItem());
		lenient().when(ingestionRunDao.createRejects(any(), any(), any())).thenReturn(Uni.createFrom().voidItem());
		lenient().when(retailerProductDao.holdChangeSequence(any())).thenReturn(Uni.createFrom().voidItem());
		lenient().when(retailerProductDao.findProducts(any(), eq(RETAILER), anyCollection())).thenAnswer(i -> {
			Collection<ProductKey> keys = i.getArgument(2);
			var found = new HashMap<ProductKey, ProductChange>();
			keys.forEach(key -> {
				if (database.containsKey(key)) found.put(key, database.get(key));
			});
			return Uni.createFrom().item(found);
		});
		lenient().when(retailerProductDao.takeChangeSequences(any(), anyInt())).thenAnswer(i -> {
			int count = i.getArgument(1);
			var first = 100L + sequencesTaken.stream().mapToInt(Integer::intValue).sum();
			sequencesTaken.add(count);
			return Uni.createFrom().item(LongStream.range(first, first + count).boxed().toList());
		});
		lenient().when(retailerProductDao.save(any(), matchesHasCatalogueId(CATALOGUE), matchesHasIngestionRunId(RUN_ID), eq(RECEIVED_AT), anyCollection())).thenAnswer(i -> {
			Collection<ProductChange> products = i.getArgument(4);
			products.forEach(product -> database.put(product.getProductKey(), product));
			return Uni.createFrom().voidItem();
		});
		lenient().when(retailerProductDao.countActiveListings(any(), matchesHasCatalogueId(CATALOGUE))).thenReturn(Uni.createFrom().item(0L));
		lenient().when(retailerProductDao.countUnseenListings(any(), matchesHasCatalogueId(CATALOGUE), matchesHasIngestionRunId(RUN_ID))).thenReturn(Uni.createFrom().item(0L));
		lenient().when(retailerProductDao.deactivateUnseenListings(any(), matchesHasCatalogueId(CATALOGUE), matchesHasIngestionRunId(RUN_ID))).thenReturn(Uni.createFrom().item(0));
		lenient().when(retailerProductDao.deactivateListings(any(), eq(RETAILER), matchesHasCatalogueId(CATALOGUE), anyCollection())).thenAnswer(i -> {
			Collection<ProductKey> keys = i.getArgument(3);
			var deactivated = 0;
			for (var key : keys) {
				var product = database.get(key);
				var listing = product != null ? product.getListings().stream().filter(l -> l.getCatalogueId().equals(CATALOGUE) && l.isActive()).findFirst() : Optional.<Listing>empty();
				if (listing.isPresent()) {
					database.put(key, ImmutableProductChange.copyOf(product).withListings(ImmutableListing.copyOf(listing.get()).withIsActive(false)));
					deactivated++;
				}
			}
			return Uni.createFrom().item(deactivated);
		});
	}

	@Test
	void appliesTheRecordsInBatchesAndCountsWhatTheyDid() {
		givenStored(record(90, "P-UNCHANGED", "1.29"), 7);
		givenStored(record(91, "P-REPRICED", "1.29"), 8);
		feed.add(record(1, "P-NEW", "2.49"));
		feed.add(new RejectedRecord(2, "P-UNREADABLE", "The price is not a number"));
		feed.add(record(3, "P-UNCHANGED", "1.29"));
		feed.add(record(4, "P-REPRICED", "1.19"));
		feed.add(record(5, null, "1.29"));

		sut.process(run(FeedType.SNAPSHOT));

		assertEquals(
				ImmutableIngestionRun.builder()
						.from(run(FeedType.SNAPSHOT))
						.status(IngestionRunStatus.DONE)
						.adapter("test-adapter")
						.adapterVersion("3")
						.startedAt(NOW)
						.finishedAt(NOW)
						.counts(ImmutableIngestionRunCounts.builder()
								.total(5).valid(3).rejected(2).added(1).changed(1).unchanged(1).missing(0).deactivated(0)
								.build())
						.build(),
				lastUpdate()
		);
		assertEquals(List.of(1, 1), sequencesTaken.stream().filter(count -> count > 0).toList(), "one stamp for the new product and one for the repriced");
		assertEquals(100L, database.get(new ProductKeyImpl("P-NEW")).getChangeSequence());
		assertEquals(7L, database.get(new ProductKeyImpl("P-UNCHANGED")).getChangeSequence());
		assertEquals(101L, database.get(new ProductKeyImpl("P-REPRICED")).getChangeSequence());
		var rejects = ArgumentCaptor.<List<IngestionReject>>captor();
		verify(ingestionRunDao, atLeastOnce()).createRejects(any(), matchesHasIngestionRunId(RUN_ID), rejects.capture());
		assertEquals(
				List.of(reject(2, "P-UNREADABLE", "The price is not a number"), reject(5, null, "The product key is missing")),
				rejects.getAllValues().stream().flatMap(List::stream).toList()
		);
	}

	@Test
	void marksTheRunProcessingAndForgetsEarlierRejectsBeforeReading() {
		feed.add(record(1, "P-1", "1.29"));

		sut.process(run(FeedType.DELTA));

		var order = inOrder(ingestionRunDao, feedStore);
		var processing = ArgumentCaptor.forClass(IngestionRun.class);
		order.verify(ingestionRunDao).update(any(), processing.capture());
		order.verify(ingestionRunDao).deleteRejects(any(), matchesHasIngestionRunId(run(FeedType.DELTA)));
		order.verify(feedStore).open("rimi-lt/run.gz");
		assertEquals(
				ImmutableIngestionRun.builder()
						.from(run(FeedType.DELTA))
						.status(IngestionRunStatus.PROCESSING)
						.adapter("test-adapter")
						.adapterVersion("3")
						.startedAt(NOW)
						.build(),
				processing.getValue()
		);
	}

	@Test
	void recordsOfOneProductInOneBatchApplyInTurn() {
		feed.add(record(1, "P-1", "1.29"));
		feed.add(record(2, "P-1", "1.19"));

		sut.process(run(FeedType.SNAPSHOT));

		var counts = lastUpdate().getCounts();
		assertEquals(1, counts.getAdded());
		assertEquals(1, counts.getChanged());
		assertEquals(List.of(1), sequencesTaken.stream().filter(count -> count > 0).toList());
		assertEquals(new BigDecimal("1.19"), database.get(new ProductKeyImpl("P-1")).getListings().getFirst().getPrice());
	}

	@Test
	void aRejectKeepsNoProductKeyTooLongToStore() {
		feed.add(new RejectedRecord(1, "k".repeat(256), "Unreadable"));
		feed.add(record(2, "k".repeat(256), "1.29"));

		sut.process(run(FeedType.SNAPSHOT));

		var rejects = ArgumentCaptor.<List<IngestionReject>>captor();
		verify(ingestionRunDao).createRejects(any(), any(), rejects.capture());
		assertEquals(
				List.of(reject(1, null, "Unreadable"), reject(2, null, "The product key is longer than 255 characters")),
				rejects.getValue()
		);
	}

	@Test
	void anEmptyFeedIsDone() {
		sut.process(run(FeedType.SNAPSHOT));

		assertEquals(IngestionRunStatus.DONE, lastUpdate().getStatus());
		assertEquals(
				ImmutableIngestionRunCounts.builder().total(0).valid(0).rejected(0).added(0).changed(0).unchanged(0).missing(0).deactivated(0).build(),
				lastUpdate().getCounts()
		);
	}

	@Test
	void aFeedThatBreaksOffFailsTheRun() {
		feed.add(record(1, "P-1", "1.29"));
		feed.add(null);

		sut.process(run(FeedType.SNAPSHOT));

		assertFailed("The Feed breaks off, position: 2");
	}

	@Test
	void aCatalogueWithAnUnknownAdapterFailsTheRun() {
		when(catalogueDao.find(any(), matchesHasCatalogueId(CATALOGUE))).thenReturn(Uni.createFrom().item(catalogue("missing-adapter")));

		sut.process(run(FeedType.SNAPSHOT));

		assertFailed("No adapter has the name the Catalogue gives, adapter: missing-adapter");
		assertNull(lastUpdate().getAdapterVersion());
		verify(feedStore, never()).open(any());
	}

	@Test
	void aSnapshotDeactivatesTheListingsItDidNotCarryUpToTheShare() {
		feed.add(record(1, "P-1", "1.29"));
		when(retailerProductDao.countActiveListings(any(), matchesHasCatalogueId(CATALOGUE))).thenReturn(Uni.createFrom().item(10L));
		when(retailerProductDao.countUnseenListings(any(), matchesHasCatalogueId(CATALOGUE), matchesHasIngestionRunId(RUN_ID))).thenReturn(Uni.createFrom().item(1L));
		when(retailerProductDao.deactivateUnseenListings(any(), matchesHasCatalogueId(CATALOGUE), matchesHasIngestionRunId(RUN_ID))).thenReturn(Uni.createFrom().item(1));

		sut.process(run(FeedType.SNAPSHOT));

		var done = lastUpdate();
		assertEquals(IngestionRunStatus.DONE, done.getStatus());
		assertEquals(NOW, done.getFinishedAt());
		assertEquals(
				ImmutableIngestionRunCounts.builder().total(1).valid(1).rejected(0).added(1).changed(0).unchanged(0).missing(1).deactivated(1).build(),
				done.getCounts()
		);
		var deactivation = ArgumentCaptor.forClass(ReactivePersistenceTxContext.class);
		verify(retailerProductDao).deactivateUnseenListings(deactivation.capture(), matchesHasCatalogueId(CATALOGUE), matchesHasIngestionRunId(RUN_ID));
		assertSame(deactivation.getValue(), lastUpdateTransaction(), "the deactivation commits with the end of the run");
	}

	@Test
	void aSnapshotThatWouldDeactivateMoreThanTheShareIsHeld() {
		feed.add(record(1, "P-1", "1.29"));
		when(retailerProductDao.countActiveListings(any(), matchesHasCatalogueId(CATALOGUE))).thenReturn(Uni.createFrom().item(10L));
		when(retailerProductDao.countUnseenListings(any(), matchesHasCatalogueId(CATALOGUE), matchesHasIngestionRunId(RUN_ID))).thenReturn(Uni.createFrom().item(2L));

		sut.process(run(FeedType.SNAPSHOT));

		var held = lastUpdate();
		assertEquals(IngestionRunStatus.HELD, held.getStatus());
		assertNull(held.getFinishedAt());
		assertEquals(
				ImmutableIngestionRunCounts.builder().total(1).valid(1).rejected(0).added(1).changed(0).unchanged(0).missing(2).deactivated(0).build(),
				held.getCounts()
		);
		assertEquals(Set.of(new ProductKeyImpl("P-1")), database.keySet(), "the records are applied before the hold");
		verify(retailerProductDao, never()).deactivateUnseenListings(any(), any(), any());
	}

	@Test
	void aDeltaDeactivatesNothing() {
		feed.add(record(1, "P-1", "1.29"));

		sut.process(run(FeedType.DELTA));

		var done = lastUpdate();
		assertEquals(IngestionRunStatus.DONE, done.getStatus());
		assertEquals(0, done.getCounts().getMissing());
		assertEquals(0, done.getCounts().getDeactivated());
		verify(retailerProductDao, never()).countUnseenListings(any(), any(), any());
		verify(retailerProductDao, never()).deactivateUnseenListings(any(), any(), any());
	}

	@Test
	void aDeletionFeedDeactivatesTheListingsOfItsProductsInTheCatalogue() {
		givenStored(record(90, "P-ACTIVE", "1.29"), 7);
		givenStored(record(91, "P-TWICE", "1.29"), 7);
		givenStored(record(92, "P-INACTIVE", "1.29"), 7);
		database.computeIfPresent(new ProductKeyImpl("P-INACTIVE"), (key, product) -> ImmutableProductChange.copyOf(product)
				.withListings(ImmutableListing.copyOf(product.getListings().getFirst()).withIsActive(false)));
		var elsewhere = ProductRecordMerger.merge(null, record(93, "P-ELSEWHERE", "1.29"), RETAILER, OTHER_CATALOGUE).product();
		database.put(elsewhere.getProductKey(), elsewhere);
		deletions.add(new DeletionRecord(1, "P-ACTIVE"));
		deletions.add(new RejectedRecord(2, null, "The record is not a JSON object"));
		deletions.add(new DeletionRecord(3, "P-INACTIVE"));
		deletions.add(new DeletionRecord(4, "P-UNKNOWN"));
		deletions.add(new DeletionRecord(5, null));
		deletions.add(new DeletionRecord(6, "P-ELSEWHERE"));
		deletions.add(new DeletionRecord(7, "P-TWICE"));
		deletions.add(new DeletionRecord(8, "P-TWICE"));
		deletions.add(new DeletionRecord(9, "P-ACTIVE"));

		sut.process(run(FeedType.DELETION));

		var done = lastUpdate();
		assertEquals(IngestionRunStatus.DONE, done.getStatus());
		assertEquals(
				ImmutableIngestionRunCounts.builder().total(9).valid(5).rejected(4).added(0).changed(0).unchanged(3).missing(0).deactivated(2).build(),
				done.getCounts()
		);
		assertEquals(
				List.of(Set.of(new ProductKeyImpl("P-ACTIVE")), Set.of(new ProductKeyImpl("P-INACTIVE")), Set.of(new ProductKeyImpl("P-TWICE")), Set.of(new ProductKeyImpl("P-ACTIVE"))),
				deactivatedKeys()
		);
		var rejects = ArgumentCaptor.<List<IngestionReject>>captor();
		verify(ingestionRunDao, atLeastOnce()).createRejects(any(), matchesHasIngestionRunId(RUN_ID), rejects.capture());
		assertEquals(
				List.of(
						reject(2, null, "The record is not a JSON object"),
						reject(4, "P-UNKNOWN", "The Catalogue has no Listing of the product"),
						reject(5, null, "The product key is missing"),
						reject(6, "P-ELSEWHERE", "The Catalogue has no Listing of the product")
				),
				rejects.getAllValues().stream().flatMap(List::stream).toList()
		);
		verify(retailerProductDao, never()).save(any(), any(), any(), any(), anyCollection());
		verify(retailerProductDao, never()).deactivateUnseenListings(any(), any(), any());
	}

	/**
	 * Otherwise the eviction could change the products of a batch between their reading and their saving.
	 */
	@Test
	void eachBatchHoldsTheChangeSequenceBeforeItReadsItsProducts() {
		feed.add(record(1, "P-1", "1.29"));
		feed.add(record(2, "P-2", "1.29"));
		feed.add(record(3, "P-3", "1.29"));

		sut.process(run(FeedType.DELTA));

		var order = inOrder(retailerProductDao);
		for (int batch = 0; batch < 2; batch++) {
			order.verify(retailerProductDao).holdChangeSequence(any());
			order.verify(retailerProductDao).findProducts(any(), eq(RETAILER), anyCollection());
		}
	}

	@Test
	void eachBatchOfADeletionFeedHoldsTheChangeSequenceBeforeItReadsItsProducts() {
		deletions.add(new DeletionRecord(1, "P-1"));
		deletions.add(new DeletionRecord(2, "P-2"));
		deletions.add(new DeletionRecord(3, "P-3"));

		sut.process(run(FeedType.DELETION));

		var order = inOrder(retailerProductDao);
		for (int batch = 0; batch < 2; batch++) {
			order.verify(retailerProductDao).holdChangeSequence(any());
			order.verify(retailerProductDao).findProducts(any(), eq(RETAILER), anyCollection());
		}
	}

	private void assertFailed(String error) {
		var run = lastUpdate();
		assertEquals(IngestionRunStatus.FAILED, run.getStatus());
		assertEquals(error, run.getError());
		assertEquals(NOW, run.getFinishedAt());
		assertNull(run.getCounts());
	}

	/**
	 * The keys handed to the deactivation, a set per batch that has any.
	 */
	private List<Set<ProductKey>> deactivatedKeys() {
		var keys = ArgumentCaptor.<Collection<ProductKey>>captor();
		verify(retailerProductDao, atLeastOnce()).deactivateListings(any(), eq(RETAILER), matchesHasCatalogueId(CATALOGUE), keys.capture());
		return keys.getAllValues().stream().filter(batch -> !batch.isEmpty()).<Set<ProductKey>>map(Set::copyOf).toList();
	}

	private ReactivePersistenceTxContext lastUpdateTransaction() {
		var transactions = ArgumentCaptor.forClass(ReactivePersistenceTxContext.class);
		verify(ingestionRunDao, atLeastOnce()).update(transactions.capture(), any());
		return transactions.getAllValues().getLast();
	}

	private IngestionRun lastUpdate() {
		var runs = ArgumentCaptor.forClass(IngestionRun.class);
		verify(ingestionRunDao, atLeastOnce()).update(any(), runs.capture());
		return runs.getAllValues().getLast();
	}

	private void givenStored(FeedRecord record, long changeSequence) {
		var product = ProductRecordMerger.merge(null, record, RETAILER, CATALOGUE).product();
		database.put(product.getProductKey(), ImmutableProductChange.copyOf(product).withChangeSequence(changeSequence));
	}

	private static IngestionRun run(FeedType feedType) {
		return ImmutableIngestionRun.builder()
				.id(RUN_ID)
				.feed(ImmutableFeed.builder()
						.id(new FeedIdImpl("3a0c5e7d-9b1f-4d2e-8c6a-5f4b3e2d1c0a"))
						.retailerId(RETAILER)
						.feedType(feedType)
						.receivedAt(RECEIVED_AT)
						.filePath("rimi-lt/run.gz")
						.fileSize(10L)
						.fileSha256("aa")
						.build())
				.catalogueId(CATALOGUE)
				.status(IngestionRunStatus.RECEIVED)
				.build();
	}

	private static Catalogue catalogue(String adapter) {
		return ImmutableCatalogue.builder()
				.id(CATALOGUE)
				.retailerId(RETAILER)
				.country("LT")
				.primaryLanguage("lt")
				.acceptedLanguages(Set.of("lt", "en"))
				.adapter(adapter)
				.isActive(true)
				.build();
	}

	private static FeedRecord record(long position, String productKey, String price) {
		return new FeedRecord(position, productKey, Map.of("lt", "Pienas"), Map.of(), List.of(), new BigDecimal(price), "EUR");
	}

	private static IngestionReject reject(long position, String productKey, String reason) {
		return ImmutableIngestionReject.builder()
				.position(position)
				.productKey(productKey != null ? new ProductKeyImpl(productKey) : null)
				.reason(reason)
				.build();
	}

	private class TestFeedReader implements FeedReader {
		@Override
		public String getName() {
			return "test-adapter";
		}

		@Override
		public String getVersion() {
			return "3";
		}

		@Override
		public Stream<FeedItem> read(Catalogue catalogue, InputStream in) {
			assertEquals(catalogue("test-adapter"), catalogue);
			assertEquals(storedFeed, in);
			return feed.stream().map(item -> {
				if (item == null) throw new UncheckedIOException("The Feed breaks off, position: 2", new IOException("Unexpected end of input"));
				return item;
			});
		}

		@Override
		public Stream<DeletionItem> readDeletions(Catalogue catalogue, InputStream in) {
			assertEquals(catalogue("test-adapter"), catalogue);
			assertEquals(storedFeed, in);
			return deletions.stream();
		}
	}
}
