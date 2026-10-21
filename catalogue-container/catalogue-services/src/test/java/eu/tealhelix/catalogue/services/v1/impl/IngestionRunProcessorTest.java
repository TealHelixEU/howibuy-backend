package eu.tealhelix.catalogue.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import jakarta.enterprise.inject.Instance;

import eu.tealhelix.catalogue.dao.CatalogueDao;
import eu.tealhelix.catalogue.dao.IngestionRunDao;
import eu.tealhelix.catalogue.dao.RetailerProductDao;
import eu.tealhelix.catalogue.feeds.FeedItem;
import eu.tealhelix.catalogue.feeds.FeedReader;
import eu.tealhelix.catalogue.feeds.FeedRecord;
import eu.tealhelix.catalogue.feeds.RejectedRecord;
import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableCatalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionReject;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRun;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRunCounts;
import eu.tealhelix.catalogue.v1.model.ImmutableProductChange;
import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
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
	private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 10, 21, 9, 0, 0);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 10, 0, 0);
	private static final int BATCH_SIZE = 2;

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

	/** The products in the database, by key, as the processor stores them. */
	private final Map<ProductKey, ProductChange> database = new HashMap<>();

	private final List<Integer> sequencesTaken = new ArrayList<>();

	private final InputStream storedFeed = new ByteArrayInputStream(new byte[0]);

	private IngestionRunProcessor sut;

	@BeforeEach
	void setUp() {
		sut = new IngestionRunProcessor(catalogueDao, ingestionRunDao, retailerProductDao, feedStore, dateTimeService, uniAwaiter, persistenceContextFactory, feedReaders, BATCH_SIZE);
		lenient().when(feedReaders.stream()).thenAnswer(i -> Stream.of(new TestFeedReader()));
		lenient().when(catalogueDao.find(any(), eq(CATALOGUE))).thenReturn(Uni.createFrom().item(catalogue("test-adapter")));
		lenient().when(dateTimeService.getNow()).thenReturn(NOW);
		lenient().when(feedStore.open("rimi-lt/run.gz")).thenReturn(storedFeed);
		lenient().when(ingestionRunDao.update(any(), any())).thenReturn(Uni.createFrom().voidItem());
		lenient().when(ingestionRunDao.deleteRejects(any(), any())).thenReturn(Uni.createFrom().voidItem());
		lenient().when(ingestionRunDao.createRejects(any(), any(), any())).thenReturn(Uni.createFrom().voidItem());
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
		lenient().when(retailerProductDao.save(any(), eq(CATALOGUE), eq(run(FeedType.SNAPSHOT).getId()), eq(RECEIVED_AT), anyCollection())).thenAnswer(i -> {
			Collection<ProductChange> products = i.getArgument(4);
			products.forEach(product -> database.put(product.getProductKey(), product));
			return Uni.createFrom().voidItem();
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
								.total(5).valid(3).rejected(2).added(1).changed(1).unchanged(1).deactivated(0)
								.build())
						.build(),
				lastUpdate()
		);
		assertEquals(List.of(1, 1), sequencesTaken.stream().filter(count -> count > 0).toList(), "one stamp for the new product and one for the repriced");
		assertEquals(100L, database.get(new ProductKeyImpl("P-NEW")).getChangeSequence());
		assertEquals(7L, database.get(new ProductKeyImpl("P-UNCHANGED")).getChangeSequence());
		assertEquals(101L, database.get(new ProductKeyImpl("P-REPRICED")).getChangeSequence());
		var rejects = ArgumentCaptor.<List<IngestionReject>>captor();
		verify(ingestionRunDao, atLeastOnce()).createRejects(any(), eq(run(FeedType.SNAPSHOT).getId()), rejects.capture());
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
		order.verify(ingestionRunDao).deleteRejects(any(), eq(run(FeedType.DELTA).getId()));
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
				ImmutableIngestionRunCounts.builder().total(0).valid(0).rejected(0).added(0).changed(0).unchanged(0).deactivated(0).build(),
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
		when(catalogueDao.find(any(), eq(CATALOGUE))).thenReturn(Uni.createFrom().item(catalogue("missing-adapter")));

		sut.process(run(FeedType.SNAPSHOT));

		assertFailed("No adapter has the name the Catalogue gives, adapter: missing-adapter");
		assertNull(lastUpdate().getAdapterVersion());
		verify(feedStore, never()).open(any());
	}

	@Test
	void aDeletionFeedFailsTheRun() {
		sut.process(run(FeedType.DELETION));

		assertFailed("Deletion Feeds are not processed yet");
		verify(feedStore, never()).open(any());
	}

	private void assertFailed(String error) {
		var run = lastUpdate();
		assertEquals(IngestionRunStatus.FAILED, run.getStatus());
		assertEquals(error, run.getError());
		assertEquals(NOW, run.getFinishedAt());
		assertNull(run.getCounts());
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
				.id(new IngestionRunIdImpl("5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e01"))
				.catalogueId(CATALOGUE)
				.feedType(feedType)
				.status(IngestionRunStatus.RECEIVED)
				.receivedAt(RECEIVED_AT)
				.filePath("rimi-lt/run.gz")
				.fileSize(10L)
				.fileSha256("aa")
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
		public Stream<FeedItem> read(InputStream in) {
			assertEquals(storedFeed, in);
			return feed.stream().map(item -> {
				if (item == null) throw new UncheckedIOException("The Feed breaks off, position: 2", new IOException("Unexpected end of input"));
				return item;
			});
		}
	}
}
