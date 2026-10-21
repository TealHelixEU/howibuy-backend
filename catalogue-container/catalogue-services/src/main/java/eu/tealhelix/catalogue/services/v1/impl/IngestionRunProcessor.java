package eu.tealhelix.catalogue.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.dao.CatalogueDao;
import eu.tealhelix.catalogue.dao.IngestionRunDao;
import eu.tealhelix.catalogue.dao.RetailerProductDao;
import eu.tealhelix.catalogue.feeds.FeedItem;
import eu.tealhelix.catalogue.feeds.FeedReader;
import eu.tealhelix.catalogue.feeds.FeedRecord;
import eu.tealhelix.catalogue.feeds.RejectedRecord;
import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionReject;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRun;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRunCounts;
import eu.tealhelix.catalogue.v1.model.ImmutableProductChange;
import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.model.IngestionRunCounts;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.services.generic.DateTimeService;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes a received Ingestion Run: reads its stored Feed with the adapter of its Catalogue, validates the records and
 * applies the valid ones to the Retailer Products, a batch per transaction. It blocks while it does, so it must be called
 * where blocking is allowed. Applying is idempotent, so a run interrupted half-way can be processed again from the start.
 */
@ApplicationScoped
public class IngestionRunProcessor {
	public static final String BATCH_SIZE_KEY = "catalogue.ingestion.batch-size";

	private static final Logger LOG = LoggerFactory.getLogger(IngestionRunProcessor.class);

	private final CatalogueDao catalogueDao;
	private final IngestionRunDao ingestionRunDao;
	private final RetailerProductDao retailerProductDao;
	private final FeedStore feedStore;
	private final DateTimeService dateTimeService;
	private final UniAwaiter uniAwaiter;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final Instance<FeedReader> feedReaders;
	private final int batchSize;

	@Inject
	public IngestionRunProcessor(
			CatalogueDao catalogueDao,
			IngestionRunDao ingestionRunDao,
			RetailerProductDao retailerProductDao,
			FeedStore feedStore,
			DateTimeService dateTimeService,
			UniAwaiter uniAwaiter,
			ReactivePersistenceContextFactory persistenceContextFactory,
			@Any Instance<FeedReader> feedReaders,
			@ConfigProperty(name = BATCH_SIZE_KEY) int batchSize
	) {
		this.catalogueDao = catalogueDao;
		this.ingestionRunDao = ingestionRunDao;
		this.retailerProductDao = retailerProductDao;
		this.feedStore = feedStore;
		this.dateTimeService = dateTimeService;
		this.uniAwaiter = uniAwaiter;
		this.persistenceContextFactory = persistenceContextFactory;
		this.feedReaders = feedReaders;
		this.batchSize = batchSize;
	}

	/**
	 * Process a run to its end, {@link IngestionRunStatus#DONE DONE} with its counts or {@link IngestionRunStatus#FAILED
	 * FAILED} with the error. The rejects of an earlier, interrupted processing are forgotten first.
	 *
	 * @param run The run, {@link IngestionRunStatus#RECEIVED RECEIVED}
	 */
	public void process(IngestionRun run) {
		var catalogue = uniAwaiter.await(() -> persistenceContextFactory.withoutTransaction(em -> catalogueDao.find(em, run.getCatalogueId())));
		var reader = feedReaders.stream().filter(r -> r.getName().equals(catalogue.getAdapter())).findFirst().orElse(null);
		var started = ImmutableIngestionRun.builder()
				.from(run)
				.status(IngestionRunStatus.PROCESSING)
				.adapter(catalogue.getAdapter())
				.adapterVersion(reader != null ? reader.getVersion() : null)
				.startedAt(dateTimeService.getNow())
				.build();
		uniAwaiter.await(() -> persistenceContextFactory.withTransaction(tx -> forc(
				ingestionRunDao.update(tx, started),
				() -> ingestionRunDao.deleteRejects(tx, run.getId())
		)));
		IngestionRun finished;
		try {
			var counts = apply(started, catalogue, reader);
			finished = ImmutableIngestionRun.builder().from(started).status(IngestionRunStatus.DONE).counts(counts).finishedAt(dateTimeService.getNow()).build();
		} catch (FeedProcessingException | UncheckedIOException e) {
			finished = failed(started, e.getMessage());
		} catch (RuntimeException e) {
			LOG.warn("An Ingestion Run failed unexpectedly, runId: " + run.getId().asString(), e);
			finished = failed(started, e.toString());
		}
		var result = finished;
		uniAwaiter.await(() -> persistenceContextFactory.withTransaction(tx -> ingestionRunDao.update(tx, result)));
	}

	private IngestionRun failed(IngestionRun run, String error) {
		return ImmutableIngestionRun.builder().from(run).status(IngestionRunStatus.FAILED).error(error).finishedAt(dateTimeService.getNow()).build();
	}

	private IngestionRunCounts apply(IngestionRun run, Catalogue catalogue, FeedReader reader) {
		if (run.getFeedType() == FeedType.DELETION) {
			throw new FeedProcessingException("Deletion Feeds are not processed yet");
		} else if (reader == null) {
			throw new FeedProcessingException("No adapter has the name the Catalogue gives, adapter: " + catalogue.getAdapter());
		}
		var counts = new Counts();
		try (var feed = feedStore.open(run.getFilePath()); var items = reader.read(feed)) {
			var batch = new ArrayList<FeedItem>(batchSize);
			var iterator = items.iterator();
			while (iterator.hasNext()) {
				batch.add(iterator.next());
				if (batch.size() == batchSize) {
					applyBatch(run, catalogue, batch, counts);
					batch.clear();
				}
			}
			if (!batch.isEmpty()) {
				applyBatch(run, catalogue, batch, counts);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to close a Feed, path: " + run.getFilePath(), e);
		}
		return counts.toIngestionRunCounts();
	}

	/**
	 * Apply a batch of items in one transaction: the valid records to their products, the others as rejects.
	 */
	private void applyBatch(IngestionRun run, Catalogue catalogue, List<FeedItem> items, Counts counts) {
		var rejects = new ArrayList<IngestionReject>();
		var records = new ArrayList<FeedRecord>();
		for (var item : items) {
			switch (item) {
				case RejectedRecord rejected -> rejects.add(reject(rejected.position(), rejected.productKey(), rejected.reason()));
				case FeedRecord record -> FeedRecordValidator.findFault(record, catalogue).ifPresentOrElse(
						fault -> rejects.add(reject(record.position(), record.productKey(), fault)),
						() -> records.add(record)
				);
			}
		}
		Set<ProductKey> productKeys = records.stream().map(r -> new ProductKeyImpl(r.productKey())).collect(Collectors.toSet());
		var outcomes = uniAwaiter.await(() -> persistenceContextFactory.withTransaction(tx -> forc(
				retailerProductDao.findProducts(tx, catalogue.getRetailerId(), productKeys),
				stored -> Uni.createFrom().item(BatchMerge.of(stored, records, catalogue)),
				merge -> retailerProductDao.takeChangeSequences(tx, merge.changed().size()),
				(merge, changeSequences) -> forc(
						retailerProductDao.save(tx, catalogue.getId(), run.getId(), run.getReceivedAt(), merge.stamped(changeSequences)),
						() -> ingestionRunDao.createRejects(tx, run.getId(), rejects)
				).replaceWith(merge.outcomes())
		)));
		counts.total += items.size();
		counts.rejected += rejects.size();
		outcomes.forEach(counts::add);
	}

	/**
	 * A product key too long to store, which the reject tells about anyway, is left out of it.
	 */
	private static IngestionReject reject(long position, String productKey, String reason) {
		var storable = productKey != null && !productKey.isBlank() && productKey.length() <= FeedRecordValidator.MAX_PRODUCT_KEY_LENGTH;
		return ImmutableIngestionReject.builder()
				.position(position)
				.productKey(storable ? new ProductKeyImpl(productKey) : null)
				.reason(reason)
				.build();
	}

	/**
	 * The valid records of a batch applied to their products in turn, so that a product named twice in the batch takes
	 * both records.
	 *
	 * @param products The new state of each product of the batch
	 * @param changed  The products that changed visibly, which take a new change sequence
	 * @param outcomes What each record did, in the order of the records
	 */
	private record BatchMerge(Map<ProductKey, ProductChange> products, Set<ProductKey> changed, List<RecordOutcome> outcomes) {
		static BatchMerge of(Map<ProductKey, ProductChange> stored, List<FeedRecord> records, Catalogue catalogue) {
			var products = new LinkedHashMap<ProductKey, ProductChange>();
			var changed = new LinkedHashSet<ProductKey>();
			var outcomes = new ArrayList<RecordOutcome>(records.size());
			for (var record : records) {
				var key = new ProductKeyImpl(record.productKey());
				var merge = ProductRecordMerger.merge(products.getOrDefault(key, stored.get(key)), record, catalogue.getRetailerId(), catalogue.getId());
				products.put(key, merge.product());
				outcomes.add(merge.outcome());
				if (merge.outcome() != RecordOutcome.UNCHANGED) changed.add(key);
			}
			return new BatchMerge(products, changed, outcomes);
		}

		/**
		 * @param changeSequences A value of the change sequence for each changed product, ascending
		 * @return The products, the changed ones stamped in the order they first appear in the batch
		 */
		List<ProductChange> stamped(List<Long> changeSequences) {
			var next = changeSequences.iterator();
			return products.values().stream()
					.map(product -> changed.contains(product.getProductKey())
							? ImmutableProductChange.copyOf(product).withChangeSequence(next.next())
							: product)
					.toList();
		}
	}

	private static class Counts {
		int total;
		int rejected;
		int added;
		int changed;
		int unchanged;

		void add(RecordOutcome outcome) {
			switch (outcome) {
				case ADDED -> added++;
				case CHANGED -> changed++;
				case UNCHANGED -> unchanged++;
			}
		}

		/**
		 * Applying records deactivates no Listing.
		 */
		IngestionRunCounts toIngestionRunCounts() {
			return ImmutableIngestionRunCounts.builder()
					.total(total)
					.valid(total - rejected)
					.rejected(rejected)
					.added(added)
					.changed(changed)
					.unchanged(unchanged)
					.deactivated(0)
					.build();
		}
	}
}
