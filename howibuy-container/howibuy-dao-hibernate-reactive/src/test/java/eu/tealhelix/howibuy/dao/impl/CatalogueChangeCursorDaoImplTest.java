package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.howibuy.dao.jpa.CatalogueChangeCursorEntity;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
public class CatalogueChangeCursorDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "howibuy.db.changelog.xml", "test");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	/**
	 * The last sequence of the cursor as the schema creates it, read before any test resets the cursor; null if the
	 * schema creates none.
	 */
	private static Long schemasLastSequence;

	@BeforeAll
	static void readTheSchemasCursor(Mutiny.SessionFactory sessionFactory) {
		schemasLastSequence = new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> em.find(CatalogueChangeCursorEntity.class, "ingestion"))
				.map(cursor -> cursor == null ? null : cursor.getLastSequence())
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	@BeforeEach
	void setUp(Mutiny.SessionFactory sessionFactory) {
		var cursor = new CatalogueChangeCursorEntity();
		cursor.setSource("ingestion");
		cursor.setLastSequence(0);
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> tx.merge(cursor))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	@Test
	void theSchemaStartsTheCursorAtZero() {
		assertEquals(0L, schemasLastSequence);
	}

	@Test
	void advancesFromTheSequenceItIsAt(Mutiny.SessionFactory sessionFactory) {
		assertTrue(advance(sessionFactory, 0, 7));
		assertEquals(7L, lastSequence(sessionFactory));

		assertTrue(advance(sessionFactory, 7, 12));
		assertEquals(12L, lastSequence(sessionFactory));
	}

	@Test
	void doesNotAdvanceFromAnotherSequence(Mutiny.SessionFactory sessionFactory) {
		advance(sessionFactory, 0, 7);

		assertFalse(advance(sessionFactory, 0, 9), "a page starting behind the cursor");
		assertFalse(advance(sessionFactory, 8, 9), "a page starting ahead of the cursor");
		assertEquals(7L, lastSequence(sessionFactory));
	}

	/**
	 * Another transaction moves the cursor from 0 and holds the row until it commits. An advance from 0 started meanwhile
	 * has to wait for it and then find that the cursor is no longer at 0.
	 */
	@Test
	void anAdvanceRacingAnotherOneFromTheSameSequenceFails(Mutiny.SessionFactory sessionFactory) throws Exception {
		try (var other = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
			other.setAutoCommit(false);
			try (var s = other.createStatement()) {
				s.executeUpdate("UPDATE TH_CATALOGUE_CHANGE_CURSOR SET last_sequence = 5 WHERE source = 'ingestion' AND last_sequence = 0");
			}

			var sut = new CatalogueChangeCursorDaoImpl();
			var racing = new ReactivePersistenceContextFactoryImpl(sessionFactory)
					.withTransaction(tx -> sut.advance(tx, 0, 7))
					.subscribeAsCompletionStage();
			awaitALockWait();
			other.commit();

			assertFalse(racing.toCompletableFuture().get(ASYNC_WAIT_SECONDS, TimeUnit.SECONDS));
		}
		assertEquals(5L, lastSequence(sessionFactory));
	}

	@Test
	void aMissingCursorIsAnError(Mutiny.SessionFactory sessionFactory) {
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> tx.find(CatalogueChangeCursorEntity.class, "ingestion").chain(tx::remove))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var sut = new CatalogueChangeCursorDaoImpl();

		var subscriber = new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(sut::requireLastSequence)
				.subscribe().withSubscriber(UniAssertSubscriber.create());

		var failure = subscriber.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).getFailure();
		assertInstanceOf(IllegalStateException.class, failure);
		assertEquals("The change cursor is missing, source: ingestion", failure.getMessage());
	}

	private static long lastSequence(Mutiny.SessionFactory sessionFactory) {
		var sut = new CatalogueChangeCursorDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(sut::requireLastSequence)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static boolean advance(Mutiny.SessionFactory sessionFactory, long fromSequence, long toSequence) {
		var sut = new CatalogueChangeCursorDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> sut.advance(tx, fromSequence, toSequence))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static void awaitALockWait() throws SQLException, InterruptedException {
		var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ASYNC_WAIT_SECONDS);
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement()) {
			while (true) {
				try (var rs = s.executeQuery("SELECT count(*) FROM pg_locks WHERE NOT granted")) {
					rs.next();
					if (rs.getLong(1) > 0) return;
				}
				if (System.nanoTime() > deadline) throw new IllegalStateException("No transaction started waiting for the cursor row");
				Thread.sleep(50);
			}
		}
	}
}
