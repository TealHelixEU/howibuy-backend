package eu.tealhelix.catalogue.dao.impl;

import static eu.tealhelix.catalogue.dao.impl.TestEntities.catalogue;
import static eu.tealhelix.catalogue.dao.impl.TestEntities.ingestionRun;
import static eu.tealhelix.catalogue.dao.impl.TestEntities.withTheirFeeds;
import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import eu.tealhelix.catalogue.v1.model.Feed;
import eu.tealhelix.catalogue.v1.model.ImmutableFeed;
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
import eu.tealhelix.catalogue.v1.types.impl.FeedIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
public class IngestionRunDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final RetailerId RETAILER = new GenericRetailerId("6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11");
	private static final CatalogueId CATALOGUE = new CatalogueIdImpl("test-gr");
	private static final IngestionRunId RUN_WITH_REJECTS = new IngestionRunIdImpl("5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e01");
	private static final IngestionRunId OTHER_RUN_WITH_REJECTS = new IngestionRunIdImpl("5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e02");

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "catalogue.db.changelog.xml", "test");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	/**
	 * The Retailer and the rejects are inserted with plain JDBC, not JPA: {@code IN_RETAILER} has no entity, and the
	 * tests that read the rejects are about the mapping of {@code IN_INGESTION_REJECT}, which rows written through that
	 * same mapping could not check.
	 */
	@BeforeAll
	static void setUp(Mutiny.SessionFactory sessionFactory) throws SQLException {
		execute("INSERT INTO IN_RETAILER (id, name) VALUES ('" + RETAILER.asString() + "', 'Test Retailer')");
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> tx.persist(catalogue(CATALOGUE, RETAILER, "GR", "el"))
						.chain(() -> tx.persistAll(withTheirFeeds(
								ingestionRun(RUN_WITH_REJECTS, CATALOGUE, RETAILER, IngestionRunStatus.DONE, LocalDateTime.of(2026, 10, 20, 10, 0, 0)),
								ingestionRun(OTHER_RUN_WITH_REJECTS, CATALOGUE, RETAILER, IngestionRunStatus.DONE, LocalDateTime.of(2026, 10, 20, 11, 0, 0))))))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		execute("INSERT INTO IN_INGESTION_REJECT (ingestion_run_id, position, product_key, reason) VALUES "
				+ "('" + RUN_WITH_REJECTS.asString() + "', 7, 'p-7', 'No price'), "
				+ "('" + RUN_WITH_REJECTS.asString() + "', 2, NULL, 'Unreadable record'), "
				+ "('" + RUN_WITH_REJECTS.asString() + "', 5, 'p-5', 'Unknown currency'), "
				+ "('" + OTHER_RUN_WITH_REJECTS.asString() + "', 1, 'p-1', 'No name')");
	}

	@Test
	void readsBackAReceivedRun(Mutiny.SessionFactory sessionFactory) {
		var run = ImmutableIngestionRun.builder()
				.id(newRunId())
				.feed(createFeed(sessionFactory, FeedType.DELTA, LocalDateTime.of(2026, 10, 20, 12, 30, 15, 123_456_000)))
				.catalogueId(CATALOGUE)
				.status(IngestionRunStatus.RECEIVED)
				.build();

		create(sessionFactory, run);

		assertEquals(run, find(sessionFactory, run.getId()));
	}

	@Test
	void readsBackAProcessedRun(Mutiny.SessionFactory sessionFactory) {
		var run = ImmutableIngestionRun.builder()
				.id(newRunId())
				.feed(createFeed(sessionFactory, FeedType.SNAPSHOT, LocalDateTime.of(2026, 10, 20, 12, 0, 0)))
				.catalogueId(CATALOGUE)
				.status(IngestionRunStatus.FAILED)
				.adapter("lime-fresh-xml")
				.adapterVersion("1.2")
				.startedAt(LocalDateTime.of(2026, 10, 20, 12, 0, 1))
				.finishedAt(LocalDateTime.of(2026, 10, 20, 12, 5, 0))
				.counts(ImmutableIngestionRunCounts.builder()
						.total(10).valid(8).rejected(2).added(3).changed(4).unchanged(1).missing(7).deactivated(6)
						.build())
				.error("The Feed ended before its last record")
				.build();

		create(sessionFactory, run);

		assertEquals(run, find(sessionFactory, run.getId()));
	}

	@Test
	void readsBackAReplay(Mutiny.SessionFactory sessionFactory) {
		var catalogueId = createCatalogue(sessionFactory);
		var original = createRun(sessionFactory, catalogueId, FeedType.DELTA, IngestionRunStatus.FAILED, LocalDateTime.of(2026, 10, 20, 8, 0, 0));
		var replay = ImmutableIngestionRun.builder()
				.from(find(sessionFactory, original))
				.id(newRunId())
				.status(IngestionRunStatus.RECEIVED)
				.replayOfRunId(original)
				.build();

		create(sessionFactory, replay);

		assertEquals(replay, find(sessionFactory, replay.getId()));
	}

	@Test
	void aLaterSnapshotIsFoundUnlessItFailed(Mutiny.SessionFactory sessionFactory) {
		var catalogueId = createCatalogue(sessionFactory);
		var otherCatalogueId = createCatalogue(sessionFactory);
		var receivedAt = LocalDateTime.of(2026, 10, 20, 8, 0, 0);
		createRun(sessionFactory, catalogueId, FeedType.SNAPSHOT, IngestionRunStatus.DONE, receivedAt);
		createRun(sessionFactory, catalogueId, FeedType.SNAPSHOT, IngestionRunStatus.DONE, receivedAt.minusHours(1));
		createRun(sessionFactory, catalogueId, FeedType.SNAPSHOT, IngestionRunStatus.FAILED, receivedAt.plusHours(1));
		createRun(sessionFactory, catalogueId, FeedType.DELTA, IngestionRunStatus.DONE, receivedAt.plusHours(1));
		createRun(sessionFactory, catalogueId, FeedType.DELETION, IngestionRunStatus.DONE, receivedAt.plusHours(1));
		createRun(sessionFactory, otherCatalogueId, FeedType.SNAPSHOT, IngestionRunStatus.DONE, receivedAt.plusHours(1));

		assertFalse(hasSnapshotReceivedAfter(sessionFactory, catalogueId, receivedAt), "only the same receipt, earlier ones, failed ones, other types and other Catalogues");

		for (var status : List.of(IngestionRunStatus.RECEIVED, IngestionRunStatus.PROCESSING, IngestionRunStatus.HELD, IngestionRunStatus.DONE, IngestionRunStatus.DISCARDED)) {
			var later = createCatalogue(sessionFactory);
			createRun(sessionFactory, later, FeedType.SNAPSHOT, status, receivedAt.plusSeconds(1));
			assertTrue(hasSnapshotReceivedAfter(sessionFactory, later, receivedAt), "a later Snapshot " + status);
		}
	}

	@Test
	void updatesARun(Mutiny.SessionFactory sessionFactory) {
		var received = ImmutableIngestionRun.builder()
				.id(newRunId())
				.feed(createFeed(sessionFactory, FeedType.SNAPSHOT, LocalDateTime.of(2026, 10, 20, 12, 0, 0)))
				.catalogueId(CATALOGUE)
				.status(IngestionRunStatus.RECEIVED)
				.build();
		create(sessionFactory, received);
		var done = ImmutableIngestionRun.builder()
				.from(received)
				.status(IngestionRunStatus.DONE)
				.adapter("test-adapter")
				.adapterVersion("1")
				.startedAt(LocalDateTime.of(2026, 10, 20, 12, 0, 1))
				.finishedAt(LocalDateTime.of(2026, 10, 20, 12, 0, 2))
				.counts(ImmutableIngestionRunCounts.builder()
						.total(3).valid(2).rejected(1).added(1).changed(1).unchanged(0).missing(0).deactivated(0)
						.build())
				.build();

		inTransaction(sessionFactory, (sut, tx) -> sut.update(tx, done));

		assertEquals(done, find(sessionFactory, received.getId()));
	}

	@Test
	void updatesARunThatIsHeld(Mutiny.SessionFactory sessionFactory) {
		var held = heldRun(sessionFactory);
		create(sessionFactory, held);
		var done = ImmutableIngestionRun.builder()
				.from(held)
				.status(IngestionRunStatus.DONE)
				.finishedAt(LocalDateTime.of(2026, 10, 20, 15, 0, 0))
				.counts(ImmutableIngestionRunCounts.copyOf(held.getCounts()).withDeactivated(5))
				.build();

		assertTrue(updateHeld(sessionFactory, done));

		assertEquals(done, find(sessionFactory, held.getId()));
	}

	@Test
	void leavesARunThatIsNoLongerHeld(Mutiny.SessionFactory sessionFactory) {
		var held = heldRun(sessionFactory);
		create(sessionFactory, held);
		var discarded = ImmutableIngestionRun.builder().from(held).status(IngestionRunStatus.DISCARDED).finishedAt(LocalDateTime.of(2026, 10, 20, 15, 0, 0)).build();
		assertTrue(updateHeld(sessionFactory, discarded));
		var done = ImmutableIngestionRun.builder().from(held).status(IngestionRunStatus.DONE).finishedAt(LocalDateTime.of(2026, 10, 20, 15, 1, 0)).build();

		assertFalse(updateHeld(sessionFactory, done));

		assertEquals(discarded, find(sessionFactory, held.getId()));
	}

	@Test
	void replacesTheRejectsOfARun(Mutiny.SessionFactory sessionFactory) {
		var runId = createRun(sessionFactory, CATALOGUE, IngestionRunStatus.PROCESSING, LocalDateTime.of(2026, 10, 20, 13, 0, 0));
		inTransaction(sessionFactory, (sut, tx) -> sut.createRejects(tx, runId, List.of(reject(3, "p-3", "No price"), reject(1, null, "Unreadable record"))));

		inTransaction(sessionFactory, (sut, tx) -> sut.deleteRejects(tx, runId));
		inTransaction(sessionFactory, (sut, tx) -> sut.createRejects(tx, runId, List.of(reject(2, "p-2", "No name"))));

		assertEquals(List.of(reject(2, "p-2", "No name")), findRejects(sessionFactory, runId, 0, 10));
	}

	@Test
	void theNextRunToProcessIsTheOldestReceived(Mutiny.SessionFactory sessionFactory) {
		var catalogueId = createCatalogue(sessionFactory);
		createRun(sessionFactory, catalogueId, IngestionRunStatus.DONE, LocalDateTime.of(2026, 10, 20, 8, 0, 0));
		createRun(sessionFactory, catalogueId, IngestionRunStatus.RECEIVED, LocalDateTime.of(2026, 10, 20, 10, 0, 0));
		var oldest = createRun(sessionFactory, catalogueId, IngestionRunStatus.RECEIVED, LocalDateTime.of(2026, 10, 20, 9, 0, 0));
		createRun(sessionFactory, CATALOGUE, IngestionRunStatus.RECEIVED, LocalDateTime.of(2026, 10, 20, 7, 0, 0));

		assertEquals(oldest, findNextToProcess(sessionFactory, catalogueId).getId());
	}

	@Test
	void noRunIsNextWhileOneIsProcessing(Mutiny.SessionFactory sessionFactory) {
		var catalogueId = createCatalogue(sessionFactory);
		createRun(sessionFactory, catalogueId, IngestionRunStatus.PROCESSING, LocalDateTime.of(2026, 10, 20, 8, 0, 0));
		createRun(sessionFactory, catalogueId, IngestionRunStatus.RECEIVED, LocalDateTime.of(2026, 10, 20, 9, 0, 0));

		assertNull(findNextToProcess(sessionFactory, catalogueId));
	}

	@Test
	void noRunIsNextWhileOneIsHeld(Mutiny.SessionFactory sessionFactory) {
		var catalogueId = createCatalogue(sessionFactory);
		createRun(sessionFactory, catalogueId, IngestionRunStatus.HELD, LocalDateTime.of(2026, 10, 20, 8, 0, 0));
		createRun(sessionFactory, catalogueId, IngestionRunStatus.RECEIVED, LocalDateTime.of(2026, 10, 20, 9, 0, 0));

		assertNull(findNextToProcess(sessionFactory, catalogueId));
	}

	@Test
	void noRunIsNextWhenNoneIsReceived(Mutiny.SessionFactory sessionFactory) {
		var catalogueId = createCatalogue(sessionFactory);
		createRun(sessionFactory, catalogueId, IngestionRunStatus.FAILED, LocalDateTime.of(2026, 10, 20, 8, 0, 0));

		assertNull(findNextToProcess(sessionFactory, catalogueId));
	}

	@Test
	void resetsTheRunsLeftProcessingAndFindsTheCataloguesWithRunsToProcess(Mutiny.SessionFactory sessionFactory) {
		var processingCatalogue = createCatalogue(sessionFactory);
		var receivedCatalogue = createCatalogue(sessionFactory);
		var doneCatalogue = createCatalogue(sessionFactory);
		var processing = createRun(sessionFactory, processingCatalogue, IngestionRunStatus.PROCESSING, LocalDateTime.of(2026, 10, 20, 8, 0, 0));
		createRun(sessionFactory, receivedCatalogue, IngestionRunStatus.RECEIVED, LocalDateTime.of(2026, 10, 20, 8, 0, 0));
		createRun(sessionFactory, doneCatalogue, IngestionRunStatus.DONE, LocalDateTime.of(2026, 10, 20, 8, 0, 0));

		inTransaction(sessionFactory, IngestionRunDaoImpl::resetProcessing);

		assertEquals(IngestionRunStatus.RECEIVED, find(sessionFactory, processing).getStatus());
		var catalogues = new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> new IngestionRunDaoImpl().findCataloguesWithReceivedRuns(em))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertTrue(catalogues.containsAll(List.of(processingCatalogue, receivedCatalogue)), "Missing from: " + catalogues);
		assertFalse(catalogues.contains(doneCatalogue), "Unexpected in: " + catalogues);
	}

	@Test
	void readsTheRunsOfAFeed(Mutiny.SessionFactory sessionFactory) {
		var feed = createFeed(sessionFactory, FeedType.SNAPSHOT, LocalDateTime.of(2026, 10, 20, 16, 0, 0));
		var laterCatalogue = new CatalogueIdImpl("test-z" + UUID.randomUUID().toString().substring(0, 8));
		createCatalogue(sessionFactory, laterCatalogue);
		var ofTheLaterCatalogue = run(feed, laterCatalogue, IngestionRunStatus.RECEIVED, null);
		var replayed = run(feed, CATALOGUE, IngestionRunStatus.FAILED, LocalDateTime.of(2026, 10, 20, 16, 0, 1));
		var replay = ImmutableIngestionRun.builder()
				.from(run(feed, CATALOGUE, IngestionRunStatus.RECEIVED, null))
				.replayOfRunId(replayed.getId())
				.build();
		inTransaction(sessionFactory, (sut, tx) -> sut.create(tx, List.of(ofTheLaterCatalogue, replayed, replay)));
		createRun(sessionFactory, CATALOGUE, IngestionRunStatus.RECEIVED, feed.getReceivedAt());

		var runs = new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> new IngestionRunDaoImpl().findOfFeed(em, feed))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertEquals(List.of(replayed, replay, ofTheLaterCatalogue), runs);
	}

	@Test
	void anUnknownFeedHasNoRuns(Mutiny.SessionFactory sessionFactory) {
		var runs = new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> new IngestionRunDaoImpl().findOfFeed(em, new FeedIdImpl(UUID.randomUUID().toString())))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertEquals(List.of(), runs);
	}

	@Test
	void anUnknownRunIsNotFound(Mutiny.SessionFactory sessionFactory) {
		assertNull(find(sessionFactory, newRunId()));
	}

	@Test
	void readsTheRejectsOfARunInFeedOrder(Mutiny.SessionFactory sessionFactory) {
		assertEquals(
				List.of(reject(2, null, "Unreadable record"), reject(5, "p-5", "Unknown currency"), reject(7, "p-7", "No price")),
				findRejects(sessionFactory, RUN_WITH_REJECTS, 0, 10)
		);
	}

	@Test
	void readsAPageOfTheRejects(Mutiny.SessionFactory sessionFactory) {
		assertEquals(List.of(reject(5, "p-5", "Unknown currency")), findRejects(sessionFactory, RUN_WITH_REJECTS, 1, 1));
	}

	@Test
	void aPagePastTheLastRejectIsEmpty(Mutiny.SessionFactory sessionFactory) {
		assertEquals(List.of(), findRejects(sessionFactory, RUN_WITH_REJECTS, 3, 10));
	}

	/**
	 * A Snapshot held because it did not carry 5 of the active Listings of its Catalogue.
	 */
	private static IngestionRun heldRun(Mutiny.SessionFactory sessionFactory) {
		return ImmutableIngestionRun.builder()
				.id(newRunId())
				.feed(createFeed(sessionFactory, FeedType.SNAPSHOT, LocalDateTime.of(2026, 10, 20, 14, 0, 0)))
				.catalogueId(CATALOGUE)
				.status(IngestionRunStatus.HELD)
				.adapter("test-adapter")
				.adapterVersion("1")
				.startedAt(LocalDateTime.of(2026, 10, 20, 14, 0, 1))
				.counts(ImmutableIngestionRunCounts.builder()
						.total(4).valid(4).rejected(0).added(0).changed(1).unchanged(3).missing(5).deactivated(0)
						.build())
				.build();
	}

	private static CatalogueId createCatalogue(Mutiny.SessionFactory sessionFactory) {
		var catalogueId = new CatalogueIdImpl("test-" + UUID.randomUUID().toString().substring(0, 8));
		createCatalogue(sessionFactory, catalogueId);
		return catalogueId;
	}

	private static void createCatalogue(Mutiny.SessionFactory sessionFactory, CatalogueId catalogueId) {
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> tx.persist(catalogue(catalogueId, RETAILER, "GR", "el")))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	/**
	 * A run of a Feed that is recorded, not itself recorded.
	 */
	private static IngestionRun run(Feed feed, CatalogueId catalogueId, IngestionRunStatus status, LocalDateTime startedAt) {
		return ImmutableIngestionRun.builder()
				.id(newRunId())
				.feed(feed)
				.catalogueId(catalogueId)
				.status(status)
				.startedAt(startedAt)
				.build();
	}

	private static IngestionRunId createRun(Mutiny.SessionFactory sessionFactory, CatalogueId catalogueId, IngestionRunStatus status, LocalDateTime receivedAt) {
		return createRun(sessionFactory, catalogueId, FeedType.SNAPSHOT, status, receivedAt);
	}

	private static IngestionRunId createRun(Mutiny.SessionFactory sessionFactory, CatalogueId catalogueId, FeedType feedType, IngestionRunStatus status, LocalDateTime receivedAt) {
		var run = ImmutableIngestionRun.builder()
				.id(newRunId())
				.feed(createFeed(sessionFactory, feedType, receivedAt))
				.catalogueId(catalogueId)
				.status(status)
				.build();
		create(sessionFactory, run);
		return run.getId();
	}

	private static Feed createFeed(Mutiny.SessionFactory sessionFactory, FeedType feedType, LocalDateTime receivedAt) {
		var feedId = new FeedIdImpl(UUID.randomUUID().toString());
		var feed = ImmutableFeed.builder()
				.id(feedId)
				.retailerId(RETAILER)
				.feedType(feedType)
				.receivedAt(receivedAt)
				.filePath(RETAILER.asString() + "/" + feedId.asString() + ".gz")
				.fileSize(10L)
				.fileSha256("aa")
				.build();
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> new FeedDaoImpl().create(tx, feed))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		return feed;
	}

	private static IngestionRunId newRunId() {
		return new IngestionRunIdImpl(UUID.randomUUID().toString());
	}

	private static IngestionReject reject(long position, String productKey, String reason) {
		return ImmutableIngestionReject.builder()
				.position(position)
				.productKey(productKey != null ? new ProductKeyImpl(productKey) : null)
				.reason(reason)
				.build();
	}

	private static void create(Mutiny.SessionFactory sessionFactory, IngestionRun run) {
		var sut = new IngestionRunDaoImpl();
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> sut.create(tx, List.of(run)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static void inTransaction(Mutiny.SessionFactory sessionFactory, BiFunction<IngestionRunDaoImpl, ReactivePersistenceTxContext, Uni<?>> work) {
		var sut = new IngestionRunDaoImpl();
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> work.apply(sut, tx))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static boolean updateHeld(Mutiny.SessionFactory sessionFactory, IngestionRun run) {
		var sut = new IngestionRunDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> sut.updateHeld(tx, run))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static IngestionRun findNextToProcess(Mutiny.SessionFactory sessionFactory, CatalogueId catalogueId) {
		var sut = new IngestionRunDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.findNextToProcess(em, catalogueId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static boolean hasSnapshotReceivedAfter(Mutiny.SessionFactory sessionFactory, CatalogueId catalogueId, LocalDateTime receivedAt) {
		var sut = new IngestionRunDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.hasSnapshotReceivedAfter(em, catalogueId, receivedAt))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static IngestionRun find(Mutiny.SessionFactory sessionFactory, IngestionRunId runId) {
		var sut = new IngestionRunDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.find(em, runId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static List<IngestionReject> findRejects(Mutiny.SessionFactory sessionFactory, IngestionRunId runId, int offset, int limit) {
		var sut = new IngestionRunDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.findRejects(em, runId, offset, limit))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static void execute(String sql) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement()) {
			s.execute(sql);
		}
	}
}
