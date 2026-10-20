package eu.tealhelix.catalogue.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
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

	private static final String RETAILER = "6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11";
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

	@BeforeAll
	static void setUp() throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement()) {
			s.execute("INSERT INTO IN_RETAILER (id, name) VALUES ('" + RETAILER + "', 'Test Retailer')");
			s.execute("INSERT INTO IN_CATALOGUE (id, retailer_id, country, primary_language) VALUES ('" + CATALOGUE.asString() + "', '" + RETAILER + "', 'GR', 'el')");
			s.execute("INSERT INTO IN_INGESTION_RUN (id, catalogue_id, feed_type, status, received_at, file_path, file_size, file_sha256) VALUES "
					+ "('" + RUN_WITH_REJECTS.asString() + "', '" + CATALOGUE.asString() + "', 'SNAPSHOT', 'DONE', '2026-10-20 10:00:00', 'test-gr/1.gz', 10, 'aa'), "
					+ "('" + OTHER_RUN_WITH_REJECTS.asString() + "', '" + CATALOGUE.asString() + "', 'SNAPSHOT', 'DONE', '2026-10-20 11:00:00', 'test-gr/2.gz', 10, 'bb')");
			s.execute("INSERT INTO IN_INGESTION_REJECT (ingestion_run_id, position, product_key, reason) VALUES "
					+ "('" + RUN_WITH_REJECTS.asString() + "', 7, 'p-7', 'No price'), "
					+ "('" + RUN_WITH_REJECTS.asString() + "', 2, NULL, 'Unreadable record'), "
					+ "('" + RUN_WITH_REJECTS.asString() + "', 5, 'p-5', 'Unknown currency'), "
					+ "('" + OTHER_RUN_WITH_REJECTS.asString() + "', 1, 'p-1', 'No name')");
		}
	}

	@Test
	void readsBackAReceivedRun(Mutiny.SessionFactory sessionFactory) {
		var run = ImmutableIngestionRun.builder()
				.id(newRunId())
				.catalogueId(CATALOGUE)
				.feedType(FeedType.DELTA)
				.status(IngestionRunStatus.RECEIVED)
				.receivedAt(LocalDateTime.of(2026, 10, 20, 12, 30, 15, 123_456_000))
				.filePath("test-gr/received.gz")
				.fileSize(5_000_000_000L)
				.fileSha256("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
				.build();

		create(sessionFactory, run);

		assertEquals(run, find(sessionFactory, run.getId()));
	}

	@Test
	void readsBackAProcessedRun(Mutiny.SessionFactory sessionFactory) {
		var run = ImmutableIngestionRun.builder()
				.id(newRunId())
				.catalogueId(CATALOGUE)
				.feedType(FeedType.SNAPSHOT)
				.status(IngestionRunStatus.FAILED)
				.adapter("lime-fresh-xml")
				.adapterVersion("1.2")
				.receivedAt(LocalDateTime.of(2026, 10, 20, 12, 0, 0))
				.startedAt(LocalDateTime.of(2026, 10, 20, 12, 0, 1))
				.finishedAt(LocalDateTime.of(2026, 10, 20, 12, 5, 0))
				.filePath("test-gr/processed.gz")
				.fileSize(1234L)
				.fileSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
				.counts(ImmutableIngestionRunCounts.builder()
						.total(10).valid(8).rejected(2).added(3).changed(4).unchanged(1).deactivated(6)
						.build())
				.error("The Feed ended before its last record")
				.build();

		create(sessionFactory, run);

		assertEquals(run, find(sessionFactory, run.getId()));
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
				.withTransaction(tx -> sut.create(tx, run))
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
}
