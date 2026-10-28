package eu.tealhelix.catalogue.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import eu.tealhelix.catalogue.v1.model.Feed;
import eu.tealhelix.catalogue.v1.model.ImmutableFeed;
import eu.tealhelix.catalogue.v1.types.FeedId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.impl.FeedIdImpl;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
public class FeedDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final String RETAILER = "6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11";

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
	 * The Retailer is inserted with plain JDBC: {@code IN_RETAILER} has no entity.
	 */
	@BeforeAll
	static void setUp() throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement()) {
			s.execute("INSERT INTO IN_RETAILER (id, name) VALUES ('" + RETAILER + "', 'Test Retailer')");
		}
	}

	@Test
	void readsBackAFeed(Mutiny.SessionFactory sessionFactory) {
		var feedId = newFeedId();
		var feed = ImmutableFeed.builder()
				.id(feedId)
				.retailerId(new GenericRetailerId(RETAILER))
				.feedType(FeedType.DELTA)
				.receivedAt(LocalDateTime.of(2026, 10, 20, 12, 30, 15, 123_456_000))
				.filePath(RETAILER + "/" + feedId.asString() + ".gz")
				.fileSize(5_000_000_000L)
				.fileSha256("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
				.build();

		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> new FeedDaoImpl().create(tx, feed))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertEquals(feed, find(sessionFactory, feedId));
	}

	@Test
	void anUnknownFeedIsNotFound(Mutiny.SessionFactory sessionFactory) {
		assertNull(find(sessionFactory, newFeedId()));
	}

	private static FeedId newFeedId() {
		return new FeedIdImpl(UUID.randomUUID().toString());
	}

	private static Feed find(Mutiny.SessionFactory sessionFactory, FeedId feedId) {
		var sut = new FeedDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.find(em, feedId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}
}
